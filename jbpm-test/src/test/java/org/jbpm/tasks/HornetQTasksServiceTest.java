package org.jbpm.tasks;

import static junit.framework.Assert.fail;
import static org.jbpm.persistence.util.PersistenceUtil.cleanUp;
import static org.jbpm.persistence.util.PersistenceUtil.setupWithPoolingDataSource;
import static org.kie.runtime.EnvironmentName.ENTITY_MANAGER_FACTORY;

import java.io.IOException;
import java.io.Reader;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.drools.io.impl.ClassPathResource;
import org.jbpm.persistence.objects.MockUserInfo;
import org.jbpm.persistence.util.PersistenceUtil;
import org.jbpm.process.workitem.wsht.HornetQHTWorkItemHandler;
import org.jbpm.task.Group;
import org.jbpm.task.TaskService;
import org.jbpm.task.User;
import org.jbpm.task.identity.DefaultUserGroupCallbackImpl;
import org.jbpm.task.identity.UserGroupCallbackManager;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.SendIcal;
import org.jbpm.task.service.SyncTaskServiceWrapper;
import org.jbpm.task.service.TaskServiceSession;
import org.jbpm.task.service.hornetq.AsyncHornetQTaskClient;
import org.jbpm.task.service.hornetq.HornetQTaskServer;
import org.jbpm.task.utils.OnErrorAction;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kie.KnowledgeBase;
import org.kie.KnowledgeBaseFactory;
import org.kie.SystemEventListenerFactory;
import org.kie.builder.KnowledgeBuilder;
import org.kie.builder.KnowledgeBuilderConfiguration;
import org.kie.builder.KnowledgeBuilderError;
import org.kie.builder.KnowledgeBuilderFactory;
import org.kie.io.ResourceType;
import org.kie.logger.KnowledgeRuntimeLoggerFactory;
import org.kie.persistence.jpa.JPAKnowledgeService;
import org.kie.runtime.Environment;
import org.kie.runtime.StatefulKnowledgeSession;
import org.kie.runtime.process.ProcessInstance;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.compiler.ExpressionCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HornetQTasksServiceTest {

    private static Logger logger = LoggerFactory
            .getLogger(HornetQTasksServiceTest.class);
    private HashMap<String, Object> context;
    private EntityManagerFactory emf;
    private EntityManagerFactory emfTasks;
    protected Map<String, User> users;
    protected Map<String, Group> groups;
    protected org.jbpm.task.service.TaskService taskService;
    protected org.jbpm.task.TaskService hornetQTaskService;
    protected TaskServiceSession taskSession;
    protected MockUserInfo userInfo;
    protected Properties conf;
    protected HornetQTaskServer hornetQTaskServer;

    @Before
    public void setUp() throws Exception {
        context = setupWithPoolingDataSource("org.jbpm.runtime", false);
        emf = (EntityManagerFactory) context.get(ENTITY_MANAGER_FACTORY);

        conf = new Properties();
        conf.setProperty("mail.smtp.host", "localhost");
        conf.setProperty("mail.smtp.port", "1125");
        conf.setProperty("from", "from@domain.com");
        conf.setProperty("replyTo", "replyTo@domain.com");
        conf.setProperty("defaultLanguage", "en-UK");

        SendIcal.initInstance(conf);

        emfTasks = Persistence.createEntityManagerFactory("org.jbpm.task");

        userInfo = new MockUserInfo();

        taskService = new org.jbpm.task.service.TaskService(emfTasks,
                SystemEventListenerFactory.getSystemEventListener(), null);
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
        if (hornetQTaskServer != null) {
            hornetQTaskServer.stop();
            hornetQTaskServer = null;
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

        Environment env = createEnvironment();
        KnowledgeBase kbase = createKnowledgeBase("Evaluation2.bpmn");
        StatefulKnowledgeSession ksession = createSession(kbase, env);
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
        Assert.assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE ||
                processInstance.getState() == ProcessInstance.STATE_PENDING);
        
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

    private StatefulKnowledgeSession createSession(KnowledgeBase kbase, Environment env) {
        return JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null, env);
    }

    private StatefulKnowledgeSession reloadSession(StatefulKnowledgeSession ksession, KnowledgeBase kbase, Environment env) {
        int sessionId = ksession.getId();
        ksession.dispose();
        return JPAKnowledgeService.loadStatefulKnowledgeSession(sessionId, kbase, null, env);
    }

    private KnowledgeBase createKnowledgeBase(String flowFile) {
        KnowledgeBuilderConfiguration conf = KnowledgeBuilderFactory.newKnowledgeBuilderConfiguration();
        conf.setProperty("drools.dialect.java.compiler", "JANINO");
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder(conf);
        kbuilder.add(new ClassPathResource(flowFile), ResourceType.BPMN2);
        if (kbuilder.hasErrors()) {
            StringBuilder errorMessage = new StringBuilder();
            for (KnowledgeBuilderError error : kbuilder.getErrors()) {
                errorMessage.append(error.getMessage());
                errorMessage.append(System.getProperty("line.separator"));
            }
            fail(errorMessage.toString());
        }

        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addKnowledgePackages(kbuilder.getKnowledgePackages());
        return kbase;
    }

    private Environment createEnvironment() {
        Environment env = PersistenceUtil.createEnvironment(context);
        return env;
    }

    public Object eval(Reader reader,
            Map vars) {
        try {
            return eval(toString(reader),
                    vars);
        } catch (IOException e) {
            throw new RuntimeException("Exception Thrown",
                    e);
        }
    }

    public String toString(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder(1024);
        int charValue;

        while ((charValue = reader.read()) != -1) {
            sb.append((char) charValue);
        }
        return sb.toString();
    }
    
    public Object eval(String str, Map vars) {

        ParserContext context = new ParserContext();
        context.addPackageImport("org.jbpm.task");
        context.addPackageImport("org.jbpm.task.service");
        context.addPackageImport("org.jbpm.task.query");
        context.addPackageImport("java.util");

        vars.put("now", new Date());
        return MVEL.executeExpression(MVEL.compileExpression(str, context),
                vars);
    }
    
    public TaskService getHornetQTaskService(StatefulKnowledgeSession ksession) {
        if (hornetQTaskServer == null) {
            startHornetQTaskService();
        }
        
        UserGroupCallbackManager.getInstance().setCallback(
                new DefaultUserGroupCallbackImpl(
                        "classpath:/usergroups.properties"));
        HornetQHTWorkItemHandler humanTaskHandler = new HornetQHTWorkItemHandler(ksession, OnErrorAction.RETHROW);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", humanTaskHandler);
        TaskService taskService = new SyncTaskServiceWrapper(new AsyncHornetQTaskClient("hornetQTaskClient"));
        return taskService;
    }
    
    public org.jbpm.task.service.TaskService startHornetQTaskService() {
        if (taskService == null) {
            taskService = new org.jbpm.task.service.TaskService(emfTasks, SystemEventListenerFactory.getSystemEventListener());
        }
        hornetQTaskServer = new HornetQTaskServer(taskService, 5153);
        Thread thread = new Thread(hornetQTaskServer);
        thread.start();
        return taskService;
    }

}
