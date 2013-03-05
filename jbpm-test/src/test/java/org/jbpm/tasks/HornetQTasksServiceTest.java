package org.jbpm.tasks;

import static org.jbpm.persistence.util.PersistenceUtil.cleanUp;
import static org.jbpm.persistence.util.PersistenceUtil.setupWithPoolingDataSource;
import static org.kie.runtime.EnvironmentName.ENTITY_MANAGER_FACTORY;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.jbpm.persistence.objects.MockUserInfo;
import org.jbpm.task.Group;
import org.jbpm.task.User;
import org.jbpm.task.identity.DefaultUserGroupCallbackImpl;
import org.jbpm.task.identity.UserGroupCallbackManager;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.SendIcal;
import org.jbpm.task.service.TaskService;
import org.jbpm.task.service.TaskServiceSession;
import org.jbpm.test.JbpmTestCase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kie.KieBase;
import org.kie.SystemEventListenerFactory;
import org.kie.logger.KnowledgeRuntimeLoggerFactory;
import org.kie.runtime.StatefulKnowledgeSession;
import org.kie.runtime.process.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HornetQTasksServiceTest extends JbpmTestCase {

    private static Logger logger = LoggerFactory
            .getLogger(HornetQTasksServiceTest.class);
    private HashMap<String, Object> context;
    private EntityManagerFactory emf;
    private EntityManagerFactory emfTasks;
    protected Map<String, User> users;
    protected Map<String, Group> groups;
    protected TaskService taskService;
    protected org.jbpm.task.TaskService hornetQTaskService;
    protected TaskServiceSession taskSession;
    protected MockUserInfo userInfo;
    protected Properties conf;

    @Before
    public void setUp() throws Exception {
        context = setupWithPoolingDataSource("org.jbpm.runtime", false);
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

        emfTasks = Persistence.createEntityManagerFactory("org.jbpm.task");

        userInfo = new MockUserInfo();

        taskService = new TaskService(emfTasks,
                SystemEventListenerFactory.getSystemEventListener(), null);
        setTaskService(taskService);
        taskSession = taskService.createSession();

        taskService.setUserinfo(userInfo);

    }

    @After
    public void tearDown() throws Exception {
        cleanUp(context);

        if (hornetQTaskService != null) {
            System.out.println("Disposing HornetQ Task Service session");
            hornetQTaskService.disconnect();
        }
        if (taskSession != null) {
            System.out.println("Disposing session");
            taskSession.dispose();
        }
        if (emfTasks != null && emfTasks.isOpen()) {
            emfTasks.close();
        }
    }

    @Test
    public void groupTaskQueryTest() throws Exception {

        Properties userGroups = new Properties();
        userGroups.setProperty("salaboy", "");
        userGroups.setProperty("john", "PM");
        userGroups.setProperty("mary", "HR");

        KieBase kbase = createKnowledgeBase("Evaluation2.bpmn");
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        KnowledgeRuntimeLoggerFactory.newConsoleLogger(ksession);
        hornetQTaskService = getHornetQTaskService(ksession);

        UserGroupCallbackManager.getInstance().setCallback(
                new DefaultUserGroupCallbackImpl(userGroups));
        
        logger.info("### Starting process ###");
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("employee", "salaboy");
        ProcessInstance processInstance = ksession.startProcess(
                "com.sample.evaluation", parameters);

        // The process is in the first Human Task waiting for its completion
        assertProcessInstanceActive(processInstance);
        
        hornetQTaskService.connect("127.0.0.1", 5153);

        // gets salaboy's tasks
        List<TaskSummary> salaboysTasks = hornetQTaskService
                .getTasksAssignedAsPotentialOwner("salaboy", "en-UK");
        Assert.assertEquals(1, salaboysTasks.size());

        hornetQTaskService.start(salaboysTasks.get(0).getId(), "salaboy");

        hornetQTaskService.complete(salaboysTasks.get(0).getId(), "salaboy",
                null);

        System.out.println("Task completion requires some time.");
        Thread.sleep(3000);
        
        List<TaskSummary> pmsTasks = hornetQTaskService
                .getTasksAssignedAsPotentialOwner("john", "en-UK");

        Assert.assertEquals(1, pmsTasks.size());

        List<TaskSummary> hrsTasks = hornetQTaskService
                .getTasksAssignedAsPotentialOwner("mary", "en-UK");

        Assert.assertEquals(1, hrsTasks.size());

    }

}
