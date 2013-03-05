package org.jbpm.bpmn2.concurrency;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jbpm.bpmn2.objects.Status;
import org.jbpm.test.JBPMJUnitTestCase;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.kie.KieBase;
import org.kie.runtime.StatefulKnowledgeSession;
import org.kie.runtime.process.WorkItem;
import org.kie.runtime.process.WorkItemHandler;
import org.kie.runtime.process.WorkItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This test takes time and resources, please only run it locally
 */
@Ignore
public class OneProcessPerThreadTest extends JBPMJUnitTestCase {

    private static final int THREAD_COUNT = 500; // FIXME doesn't work with 1000
    private static volatile AtomicInteger started = new AtomicInteger(0);
    private static volatile AtomicInteger done = new AtomicInteger(0);

    private static Logger logger = LoggerFactory
            .getLogger(OneProcessPerThreadTest.class);

    private StatefulKnowledgeSession ksession;

    @BeforeClass
    public static void setup() throws Exception {
        if (PERSISTENCE) {
            setUpDataSource();
        }
    }

    @After
    public void dispose() {
        if (ksession != null) {
            ksession.dispose();
        }
    }

    @Test
    public void testMultiThreadProcessInstanceWorkItem() throws Exception {
        final ConcurrentHashMap<Long, Long> workItems = new ConcurrentHashMap<Long, Long>();

        try {
            KieBase kbase = createKnowledgeBase("manual/MultiThreadServiceProcess.bpmn2");

            ksession = createKnowledgeSession(kbase);

            ksession.getWorkItemManager().registerWorkItemHandler("Log",
                    new WorkItemHandler() {
                        public void executeWorkItem(WorkItem workItem,
                                WorkItemManager manager) {
                            Long threadId = (Long) workItem.getParameter("id");
                            workItems.put(workItem.getProcessInstanceId(),
                                    threadId);
                        }

                        public void abortWorkItem(WorkItem arg0,
                                WorkItemManager arg1) {
                        }
                    });

            startThreads(ksession);

            assertEquals(THREAD_COUNT, workItems.size());
        } catch (Throwable t) {
            t.printStackTrace();
            fail("Should not raise any exception: " + t.getMessage());
        }

        int i = 0;
        while (started.get() > done.get()) {
            System.out.println(started + " > " + done);
            Thread.sleep(1000);
            if (++i > 100) {
                fail("Not all threads completed.");
            }
        }
    }

    private static void startThreads(StatefulKnowledgeSession ksession)
            throws Throwable {
        boolean success = true;
        final Thread[] t = new Thread[THREAD_COUNT];

        final ProcessInstanceStartRunner[] r = new ProcessInstanceStartRunner[THREAD_COUNT];
        for (int i = 0; i < t.length; i++) {
            r[i] = new ProcessInstanceStartRunner(ksession, i,
                    "MultiThreadServiceProcess");
            t[i] = new Thread(r[i], "thread-" + i);
            try {
                t[i].start();
            } catch (Throwable fault) {
                System.out.println(i);
                fail("Unable to complete test: " + fault.getMessage());
            }
        }
        for (int i = 0; i < t.length; i++) {
            t[i].join();
            if (r[i].getStatus() == Status.FAIL) {
                success = false;
            }
        }
        if (!success) {
            fail("Multithread test failed. Look at the stack traces for details. ");
        }
    }

    public static class ProcessInstanceStartRunner implements Runnable {

        private StatefulKnowledgeSession ksession;
        private String processId;
        private long id;
        private Status status;

        public ProcessInstanceStartRunner(StatefulKnowledgeSession ksession,
                int id, String processId) {
            this.ksession = ksession;
            this.id = id;
            this.processId = processId;
        }

        public void run() {
            started.incrementAndGet();
            try {
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("id", id);
                ksession.startProcess(processId, params);
            } catch (Throwable t) {
                this.status = Status.FAIL;
                logger.error(Thread.currentThread().getName() + " failed: "
                        + t.getMessage());
                t.printStackTrace();
            }
            done.incrementAndGet();
        }

        public long getId() {
            return id;
        }

        public Status getStatus() {
            return status;
        }
    }

}
