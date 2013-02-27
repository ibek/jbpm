package org.jbpm.persistence;

import static org.jbpm.persistence.util.PersistenceUtil.cleanUp;
import static org.jbpm.persistence.util.PersistenceUtil.setupWithPoolingDataSource;
import static org.kie.runtime.EnvironmentName.ENTITY_MANAGER_FACTORY;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.drools.impl.EnvironmentFactory;
import org.drools.marshalling.impl.ClassObjectMarshallingStrategyAcceptor;
import org.drools.marshalling.impl.SerializablePlaceholderResolverStrategy;
import org.drools.persistence.jpa.marshaller.JPAPlaceholderResolverStrategy;
import org.jbpm.persistence.objects.MedicalRecord;
import org.jbpm.persistence.objects.MockUserInfo;
import org.jbpm.persistence.objects.Patient;
import org.jbpm.persistence.objects.RecordRow;
import org.jbpm.persistence.util.PersistenceUtil;
import org.jbpm.process.workitem.wsht.LocalHTWorkItemHandler;
import org.jbpm.task.Content;
import org.jbpm.task.Group;
import org.jbpm.task.Task;
import org.jbpm.task.User;
import org.jbpm.task.identity.DefaultUserGroupCallbackImpl;
import org.jbpm.task.identity.UserGroupCallbackManager;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.SendIcal;
import org.jbpm.task.service.TaskService;
import org.jbpm.task.service.TaskServiceSession;
import org.jbpm.task.service.local.LocalTaskService;
import org.jbpm.task.utils.ContentMarshallerHelper;
import org.jbpm.test.JbpmJUnitTestCase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kie.KieBase;
import org.kie.SystemEventListenerFactory;
import org.kie.marshalling.ObjectMarshallingStrategy;
import org.kie.runtime.Environment;
import org.kie.runtime.EnvironmentName;
import org.kie.runtime.StatefulKnowledgeSession;
import org.kie.runtime.process.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PatientVariablePersistenceStrategyTest extends JbpmJUnitTestCase {

    private static Logger logger = LoggerFactory
            .getLogger(PatientVariablePersistenceStrategyTest.class);
    private HashMap<String, Object> context;

    private EntityManagerFactory emf;
    private EntityManagerFactory emfDomain;
    private EntityManagerFactory emfTasks;

    protected Map<String, User> users;
    protected Map<String, Group> groups;

    protected TaskService taskService;
    protected LocalTaskService localTaskService;
    protected TaskServiceSession taskSession;

    protected MockUserInfo userInfo;
    protected Properties conf;

    protected StatefulKnowledgeSession ksession;
    protected LocalHTWorkItemHandler htHandler;

    @Before
    public void setUp() throws Exception {
        context = setupWithPoolingDataSource("org.jbpm.persistence.jpa", false);
        emf = (EntityManagerFactory) context.get(ENTITY_MANAGER_FACTORY);
        
        setEntityManagerFactory(emf);
        setPersistence(true);

        conf = new Properties();
        conf.setProperty("mail.smtp.host", "localhost");
        conf.setProperty("mail.smtp.port", "1125");
        conf.setProperty("from", "from@domain.com");
        conf.setProperty("replyTo", "replyTo@domain.com");
        conf.setProperty("defaultLanguage", "en-UK");

        SendIcal.initInstance(conf);

        // Use persistence.xml configuration
        emfDomain = Persistence
                .createEntityManagerFactory("org.jbpm.persistence.patient.example");
        emfTasks = Persistence.createEntityManagerFactory("org.jbpm.task");
        UserGroupCallbackManager.getInstance().setCallback(
                new DefaultUserGroupCallbackImpl(
                        "classpath:/usergroups.properties"));

        userInfo = new MockUserInfo();

        taskService = new TaskService(emfTasks,
                SystemEventListenerFactory.getSystemEventListener(), null);
        taskSession = taskService.createSession();

        taskService.setUserinfo(userInfo);

        localTaskService = new LocalTaskService(taskService);

    }

    @After
    public void tearDown() throws Exception {
        cleanUp(context);

        if (localTaskService != null) {
            System.out.println("Disposing Local Task Service session");
            localTaskService.disconnect();
        }
        if (taskSession != null) {
            System.out.println("Disposing session");
            taskSession.dispose();
        }
        if (emfTasks != null && emfTasks.isOpen()) {
            emfTasks.close();
        }
        if (emfDomain != null && emfDomain.isOpen()) {
            emfDomain.close();
        }
    }

    @Test
    public void simplePatientMedicalRecordTest() throws Exception {
        Patient salaboy = new Patient("salaboy");
        MedicalRecord medicalRecord = new MedicalRecord(
                "Last Three Years Medical Hisotry", salaboy);

        EntityManager em = emfDomain.createEntityManager();

        em.getTransaction().begin();
        em.persist(medicalRecord);
        em.getTransaction().commit();

        Environment env = createEnvironment();
        KieBase kbase = createKnowledgeBase("patient-appointment.bpmn");
        ksession = createKnowledgeSession(kbase, env);
        htHandler = new LocalHTWorkItemHandler(localTaskService, ksession);
        htHandler.setLocal(true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                htHandler);
        logger.info("### Starting process ###");
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("medicalRecord", medicalRecord);
        ProcessInstance process = ksession.startProcess(
                "org.jbpm.PatientAppointment", parameters);

        // The process is in the first Human Task waiting for its completion
        Assert.assertEquals(ProcessInstance.STATE_ACTIVE, process.getState());

        // gets frontDesk's tasks
        List<TaskSummary> frontDeskTasks = this.localTaskService
                .getTasksAssignedAsPotentialOwner("frontDesk", "en-UK");
        Assert.assertEquals(1, frontDeskTasks.size());

        // doctor doesn't have any task
        List<TaskSummary> doctorTasks = this.localTaskService
                .getTasksAssignedAsPotentialOwner("doctor", "en-UK");
        Assert.assertTrue(doctorTasks.isEmpty());

        // manager doesn't have any task
        List<TaskSummary> managerTasks = this.localTaskService
                .getTasksAssignedAsPotentialOwner("manager", "en-UK");
        Assert.assertTrue(managerTasks.isEmpty());

        this.localTaskService.start(frontDeskTasks.get(0).getId(), "frontDesk");
        // frontDesk completes its task
        MedicalRecord taskMedicalRecord = getTaskContent(frontDeskTasks.get(0));
        Assert.assertNotNull(taskMedicalRecord.getId());
        taskMedicalRecord
                .setDescription("Initial Description of the Medical Record");

        em.getTransaction().begin();
        em.merge(taskMedicalRecord);
        em.getTransaction().commit();

        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                htHandler);

        this.localTaskService.complete(frontDeskTasks.get(0).getId(),
                "frontDesk", null);

        // Now doctor has 1 task
        doctorTasks = this.localTaskService.getTasksAssignedAsPotentialOwner(
                "doctor", "en-UK");
        Assert.assertEquals(1, doctorTasks.size());

        // No tasks for manager yet
        managerTasks = this.localTaskService.getTasksAssignedAsPotentialOwner(
                "manager", "en-UK");
        Assert.assertTrue(managerTasks.isEmpty());

        taskMedicalRecord = getTaskContent(doctorTasks.get(0));

        this.localTaskService.start(doctorTasks.get(0).getId(), "doctor");
        // Check that we have the Modified Document
        Assert.assertEquals("Initial Description of the Medical Record",
                taskMedicalRecord.getDescription());
        em.getTransaction().begin();
        taskMedicalRecord.setDescription("Medical Record Validated by Doctor");
        List<RecordRow> rows = new ArrayList<RecordRow>();
        rows.add(new RecordRow("CODE-999", "Just a regular Cold"));
        taskMedicalRecord.setRows(rows);
        taskMedicalRecord.setPriority(1);

        em.getTransaction().commit();

        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                htHandler);

        this.localTaskService.complete(doctorTasks.get(0).getId(), "doctor",
                null);

        // tasks for manager
        managerTasks = this.localTaskService.getTasksAssignedAsPotentialOwner(
                "manager", "en-UK");
        Assert.assertEquals(1, managerTasks.size());
        this.localTaskService.start(managerTasks.get(0).getId(), "manager");

        em.getTransaction().begin();
        Patient patient = taskMedicalRecord.getPatient();
        patient.setNextAppointment(new Date());

        em.getTransaction().commit();

        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                htHandler);

        this.localTaskService.complete(managerTasks.get(0).getId(), "manager",
                null);

        // since persisted process instance is completed it should be null
        process = ksession.getProcessInstance(process.getId());
        Assert.assertNull(process);

    }

    private Environment createEnvironment() {
        Environment domainEnv = EnvironmentFactory.newEnvironment();
        domainEnv.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emfDomain);
        Environment env = PersistenceUtil.createEnvironment(context);
        env.set(EnvironmentName.OBJECT_MARSHALLING_STRATEGIES,
                new ObjectMarshallingStrategy[] {
                        new JPAPlaceholderResolverStrategy(domainEnv),
                        new SerializablePlaceholderResolverStrategy(
                                ClassObjectMarshallingStrategyAcceptor.DEFAULT) });
        return env;
    }

    private MedicalRecord getTaskContent(TaskSummary summary)
            throws IOException, ClassNotFoundException {
        logger.info(" >>> Getting Task Content = " + summary.getId());
        Content content = this.localTaskService.getContent(summary.getId());
        Task task = this.localTaskService.getTask(summary.getId());
        Object readObject = ContentMarshallerHelper.unmarshall(
                content.getContent(), ksession.getEnvironment());

        logger.info(" >>> Object = " + readObject);
        return (MedicalRecord) readObject;
    }
}
