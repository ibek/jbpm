/*
Copyright 2013 JBoss Inc

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

package org.jbpm.bpmn2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.drools.command.impl.GenericCommand;
import org.drools.command.impl.KnowledgeCommandContext;
import org.jbpm.bpmn2.JbpmBpmn2TestCase.TestWorkItemHandler;
import org.jbpm.bpmn2.handler.ReceiveTaskHandler;
import org.jbpm.bpmn2.handler.SendTaskHandler;
import org.jbpm.bpmn2.objects.Person;
import org.jbpm.process.instance.impl.ProcessInstanceImpl;
import org.jbpm.process.instance.impl.demo.DoNothingWorkItemHandler;
import org.jbpm.process.instance.impl.demo.SystemOutWorkItemHandler;
import org.jbpm.test.JbpmJUnitTestCase;
import org.jbpm.test.RequirePersistence;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.KieBase;
import org.kie.KnowledgeBase;
import org.kie.cdi.KBase;
import org.kie.command.Context;
import org.kie.event.process.DefaultProcessEventListener;
import org.kie.event.process.ProcessEventListener;
import org.kie.event.process.ProcessNodeLeftEvent;
import org.kie.event.process.ProcessNodeTriggeredEvent;
import org.kie.runtime.StatefulKnowledgeSession;
import org.kie.runtime.process.ProcessInstance;
import org.kie.runtime.process.WorkItem;
import org.kie.runtime.process.WorkflowProcessInstance;
import org.kie.runtime.rule.FactHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(CDITestRunner.class)
public class EventTest extends JbpmJUnitTestCase {

    @Inject
    @KBase("event")
    private KieBase eventBase;

    private StatefulKnowledgeSession ksession;

    private Logger logger = LoggerFactory.getLogger(EventTest.class);

    public EventTest() {

    }

    @BeforeClass
    public static void setup() throws Exception {
        if (PERSISTENCE) {
            setUpDataSource();
        }
    }

    @Before
    public void init() throws Exception {
        ksession = createKnowledgeSession(eventBase);
    }

    @After
    public void dispose() {
        if (ksession != null) {
            ksession.dispose();
        }
    }

    @Test
    @RequirePersistence(value = false, comment = "this test should work with persistence")
    public void testSignalBoundaryEventOnTask() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new TestWorkItemHandler());
        ksession.addEventListener(new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                System.out.println("After node left "
                        + event.getNodeInstance().getNodeName());
            }

            @Override
            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                System.out.println("After node triggered "
                        + event.getNodeInstance().getNodeName());
            }

            @Override
            public void beforeNodeLeft(ProcessNodeLeftEvent event) {
                System.out.println("Before node left "
                        + event.getNodeInstance().getNodeName());
            }

            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                System.out.println("Before node triggered "
                        + event.getNodeInstance().getNodeName());
            }

        });
        ProcessInstance processInstance = ksession
                .startProcess("BoundarySignalOnTask");
        ksession.signalEvent("MySignal", "value");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertNodeTriggered(processInstance.getId(), "StartProcess", "User Task", "Boundary event", "Signal received", "End2");
    }

    @Test
    @RequirePersistence(false)
    public void testSignalBoundaryEventOnTaskBetweenProcesses() {
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                handler);
        ProcessInstance processInstance = ksession
                .startProcess("BoundarySignalOnTask");

        ProcessInstance processInstance2 = ksession
                .startProcess("SignalIntermediateEvent");
        assertProcessInstanceCompleted(processInstance2.getId(), ksession);

        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    @RequirePersistence(false)
    public void testSignalBoundaryEventOnTaskComplete() throws Exception {
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                handler);
        ksession.addEventListener(new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                System.out.println("After node left "
                        + event.getNodeInstance().getNodeName());
            }

            @Override
            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                System.out.println("After node triggered "
                        + event.getNodeInstance().getNodeName());
            }

            @Override
            public void beforeNodeLeft(ProcessNodeLeftEvent event) {
                System.out.println("Before node left "
                        + event.getNodeInstance().getNodeName());
            }

            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                System.out.println("Before node triggered "
                        + event.getNodeInstance().getNodeName());
            }

        });
        ProcessInstance processInstance = ksession
                .startProcess("BoundarySignalOnTask");
        ksession.getWorkItemManager().completeWorkItem(
                handler.getWorkItem().getId(), null);
        ksession.signalEvent("MySignal", "value");
        ksession.getWorkItemManager().completeWorkItem(
                handler.getWorkItem().getId(), null);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEventBasedSplit() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        // Yes
        ProcessInstance processInstance = ksession
                .startProcess("EventBasedSplit");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        ksession.signalEvent("Yes", "YesValue", processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        // No
        processInstance = ksession.startProcess("EventBasedSplit");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        ksession.signalEvent("No", "NoValue", processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testEventBasedSplitBefore() throws Exception {
        // signaling before the split is reached should have no effect
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new DoNothingWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new DoNothingWorkItemHandler());
        // Yes
        ProcessInstance processInstance = ksession
                .startProcess("EventBasedSplit");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new DoNothingWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new DoNothingWorkItemHandler());
        ksession.signalEvent("Yes", "YesValue", processInstance.getId());
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        // No
        processInstance = ksession.startProcess("EventBasedSplit");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new DoNothingWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new DoNothingWorkItemHandler());
        ksession.signalEvent("No", "NoValue", processInstance.getId());
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testEventBasedSplitAfter() throws Exception {
        // signaling the other alternative after one has been selected should
        // have no effect
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new DoNothingWorkItemHandler());
        // Yes
        ProcessInstance processInstance = ksession
                .startProcess("EventBasedSplit");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new DoNothingWorkItemHandler());
        ksession.signalEvent("Yes", "YesValue", processInstance.getId());
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new DoNothingWorkItemHandler());
        // No
        ksession.signalEvent("No", "NoValue", processInstance.getId());
    }

    @Test
    public void testEventBasedSplit2() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        // Yes
        ProcessInstance processInstance = ksession
                .startProcess("EventBasedSplit2");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        ksession.signalEvent("Yes", "YesValue", processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        Thread.sleep(800);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        ksession.fireAllRules();
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        // Timer
        processInstance = ksession.startProcess("EventBasedSplit2");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(800);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        ksession.fireAllRules();
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testEventBasedSplit3() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        Person jack = new Person();
        jack.setName("Jack");
        // Yes
        ProcessInstance processInstance = ksession
                .startProcess("EventBasedSplit3");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        ksession.signalEvent("Yes", "YesValue", processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        // Condition
        processInstance = ksession.startProcess("EventBasedSplit3");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        ksession.insert(jack);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testEventBasedSplit4() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        // Yes
        ProcessInstance processInstance = ksession
                .startProcess("EventBasedSplit4");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        ksession.signalEvent("Message-YesMessage", "YesValue",
                processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        // No
        processInstance = ksession.startProcess("EventBasedSplit4");
        ksession.signalEvent("Message-NoMessage", "NoValue",
                processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testEventBasedSplit5() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        ReceiveTaskHandler receiveTaskHandler = new ReceiveTaskHandler(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Receive Task",
                receiveTaskHandler);
        // Yes
        ProcessInstance processInstance = ksession
                .startProcess("EventBasedSplit5");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        receiveTaskHandler.setKnowledgeRuntime(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Receive Task",
                receiveTaskHandler);
        receiveTaskHandler.messageReceived("YesMessage", "YesValue");
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        receiveTaskHandler.messageReceived("NoMessage", "NoValue");
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Email1",
                new SystemOutWorkItemHandler());
        ksession.getWorkItemManager().registerWorkItemHandler("Email2",
                new SystemOutWorkItemHandler());
        receiveTaskHandler.setKnowledgeRuntime(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Receive Task",
                receiveTaskHandler);
        // No
        processInstance = ksession.startProcess("EventBasedSplit5");
        receiveTaskHandler.messageReceived("NoMessage", "NoValue");
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        receiveTaskHandler.messageReceived("YesMessage", "YesValue");
    }

    @Test
    public void testEscalationBoundaryEvent() throws Exception {
        ProcessInstance processInstance = ksession
                .startProcess("EscalationBoundaryEvent");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testEscalationBoundaryEventInterrupting() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("EscalationBoundaryEventInterrupting");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        // TODO: check for cancellation of task
    }

    @Test
    @RequirePersistence(false)
    public void testEscalationBoundaryEventOnTask() throws Exception {
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                handler);
        ksession.addEventListener(new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                System.out.println("After node left "
                        + event.getNodeInstance().getNodeName());
            }

            @Override
            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                System.out.println("After node triggered "
                        + event.getNodeInstance().getNodeName());
            }

            @Override
            public void beforeNodeLeft(ProcessNodeLeftEvent event) {
                System.out.println("Before node left "
                        + event.getNodeInstance().getNodeName());
            }

            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                System.out.println("Before node triggered "
                        + event.getNodeInstance().getNodeName());
            }

        });
        ProcessInstance processInstance = ksession
                .startProcess("EscalationBoundaryEventOnTask");

        List<WorkItem> workItems = handler.getWorkItems();
        assertEquals(2, workItems.size());

        WorkItem workItem = workItems.get(0);
        if (!"john".equalsIgnoreCase((String) workItem.getParameter("ActorId"))) {
            workItem = workItems.get(1);
        }

        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testErrorBoundaryEvent() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("ErrorBoundaryEventInterrupting");
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    @RequirePersistence(false)
    public void testErrorBoundaryEventOnTask() throws Exception {
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                handler);
        ksession.addEventListener(new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                System.out.println("After node left "
                        + event.getNodeInstance().getNodeName());
            }

            @Override
            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                System.out.println("After node triggered "
                        + event.getNodeInstance().getNodeName());
            }

            @Override
            public void beforeNodeLeft(ProcessNodeLeftEvent event) {
                System.out.println("Before node left "
                        + event.getNodeInstance().getNodeName());
            }

            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                System.out.println("Before node triggered "
                        + event.getNodeInstance().getNodeName());
            }

        });
        ProcessInstance processInstance = ksession
                .startProcess("ErrorBoundaryEventOnTask");

        List<WorkItem> workItems = handler.getWorkItems();
        assertEquals(2, workItems.size());

        WorkItem workItem = workItems.get(0);
        if (!"john".equalsIgnoreCase((String) workItem.getParameter("ActorId"))) {
            workItem = workItems.get(1);
        }

        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testTimerBoundaryEventDuration() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("TimerBoundaryEventDuration");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(1000);
        ksession = restoreSession(ksession, true);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testTimerBoundaryEventDurationISO() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("TimerBoundaryEventDurationISO");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(1500);
        ksession = restoreSession(ksession, true);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testTimerBoundaryEventDateISO() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask",
                new DoNothingWorkItemHandler());
        HashMap<String, Object> params = new HashMap<String, Object>();
        DateTime now = new DateTime(System.currentTimeMillis());
        now.plus(2000);
        params.put("date", now.toString());
        ProcessInstance processInstance = ksession.startProcess(
                "TimerBoundaryEventDateISO", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(2000);
        ksession = restoreSession(ksession, true);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testTimerBoundaryEventCycle1() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("TimerBoundaryEventCycle1");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(1000);
        ksession = restoreSession(ksession, true);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testTimerBoundaryEventCycle2() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("TimerBoundaryEventCycle2");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(1000);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        Thread.sleep(1000);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        ksession.abortProcessInstance(processInstance.getId());
        Thread.sleep(1000);
    }

    @Test
    public void testTimerBoundaryEventCycleISO() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("TimerBoundaryEventCycleISO");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(1000);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        Thread.sleep(1000);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        ksession.abortProcessInstance(processInstance.getId());
        Thread.sleep(1000);
    }

    @Test
    public void testTimerBoundaryEventInterrupting() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("TimerBoundaryEventInterrupting");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(1000);
        ksession = restoreSession(ksession, true);
        logger.debug("Firing timer");
        ksession.fireAllRules();
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testTimerBoundaryEventInterruptingOnTask() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new TestWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("TimerBoundaryEventInterruptingOnTask");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(1000);
        ksession = restoreSession(ksession, true);
        logger.debug("Firing timer");
        ksession.fireAllRules();
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testSignalBoundaryEventInterrupting() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("SignalBoundaryEventInterrupting");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);

        ksession = restoreSession(ksession, true);
        ksession.signalEvent("MyMessage", null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testIntermediateCatchEventSignal() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("IntermediateCatchEventSignal");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        // now signal process instance
        ksession.signalEvent("MyMessage", "SomeValue", processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertNodeTriggered(processInstance.getId(), "StartProcess", "UserTask", "EndProcess", "event");
    }

    @Test
    public void testIntermediateCatchEventMessage() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("IntermediateCatchEventMessage");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        // now signal process instance
        ksession.signalEvent("Message-HelloMessage", "SomeValue",
                processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    @RequirePersistence(false)
    public void testIntermediateCatchEventTimerDuration() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("IntermediateCatchEventTimerDuration");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        // now wait for 1 second for timer to trigger
        Thread.sleep(1000);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        ksession.fireAllRules();
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    @RequirePersistence(false)
    public void testIntermediateCatchEventTimerDateISO() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        HashMap<String, Object> params = new HashMap<String, Object>();
        DateTime now = new DateTime(System.currentTimeMillis());
        now.plus(2000);
        params.put("date", now.toString());
        ProcessInstance processInstance = ksession.startProcess(
                "IntermediateCatchEventTimerDateISO", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        // now wait for 1 second for timer to trigger
        Thread.sleep(2000);
        ksession.fireAllRules();
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    @RequirePersistence(false)
    public void testIntermediateCatchEventTimerDurationISO() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("IntermediateCatchEventTimerDurationISO");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        // now wait for 1.5 second for timer to trigger
        Thread.sleep(1500);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        ksession.fireAllRules();
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    @RequirePersistence(false)
    public void testIntermediateCatchEventTimerCycle1() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("IntermediateCatchEventTimerCycle1");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        // now wait for 1 second for timer to trigger
        Thread.sleep(1000);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        ksession.fireAllRules();
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    @RequirePersistence(false)
    public void testIntermediateCatchEventTimerCycleISO() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        final List<Long> list = new ArrayList<Long>();
        ksession.addEventListener(new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName().equals("timer")) {
                    list.add(event.getProcessInstance().getId());
                }
            }

        });
        ProcessInstance processInstance = ksession
                .startProcess("IntermediateCatchEventTimerCycleISO");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);

        Thread.sleep(500);
        for (int i = 0; i < 5; i++) {
            ksession.fireAllRules();
            Thread.sleep(1000);
        }
        assertEquals(6, list.size());
    }

    @Test
    @RequirePersistence(false)
    public void testIntermediateCatchEventTimerCycle2() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("IntermediateCatchEventTimerCycle2");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        // now wait for 1 second for timer to trigger
        Thread.sleep(1000);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        Thread.sleep(1000);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        ksession.abortProcessInstance(processInstance.getId());
        Thread.sleep(1000);
    }

    @Test
    public void testIntermediateCatchEventCondition() throws Exception {
        ProcessInstance processInstance = ksession
                .startProcess("IntermediateCatchEventCondition");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        // now activate condition
        Person person = new Person();
        person.setName("Jack");
        ksession.insert(person);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testSignalBetweenProcesses() {
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                handler);

        ProcessInstance processInstance = ksession
                .startProcess("IntermediateCatchSignalSingle");
        ksession.getWorkItemManager().completeWorkItem(
                handler.getWorkItem().getId(), null);

        ProcessInstance processInstance2 = ksession
                .startProcess("SignalIntermediateEvent");
        assertProcessInstanceCompleted(processInstance2.getId(), ksession);

        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testErrorEndEventProcess() throws Exception {
        ProcessInstance processInstance = ksession
                .startProcess("ErrorEndEvent");
        assertProcessInstanceAborted(processInstance.getId(), ksession);
        assertEquals("error",
                ((org.jbpm.process.instance.ProcessInstance) processInstance)
                        .getOutcome());
    }

    @Test
    public void testEscalationEndEventProcess() throws Exception {
        ProcessInstance processInstance = ksession
                .startProcess("EscalationEndEvent");
        assertProcessInstanceAborted(processInstance.getId(), ksession);
    }

    @Test
    public void testEscalationIntermediateThrowEventProcess() throws Exception {
        ProcessInstance processInstance = ksession
                .startProcess("EscalationIntermediateThrowEvent");
        assertProcessInstanceAborted(processInstance.getId(), ksession);
    }

    @Test
    public void testCompensateIntermediateThrowEventProcess() throws Exception {
        ProcessInstance processInstance = ksession
                .startProcess("CompensateIntermediateThrowEvent");
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testMessageIntermediateThrow() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Send Task",
                new SendTaskHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "MyValue");
        ProcessInstance processInstance = ksession.startProcess(
                "MessageIntermediateEvent", params);
        assertEquals(ProcessInstance.STATE_COMPLETED,
                processInstance.getState());
    }

    @Test
    public void testSignalIntermediateThrow() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "MyValue");
        ProcessInstance processInstance = ksession.startProcess(
                "SignalIntermediateEvent", params);
        assertEquals(ProcessInstance.STATE_COMPLETED,
                processInstance.getState());
    }

    @Test
    public void testNoneIntermediateThrow() throws Exception {
        ProcessInstance processInstance = ksession.startProcess(
                "NoneIntermediateEvent", null);
        assertEquals(ProcessInstance.STATE_COMPLETED,
                processInstance.getState());
    }

    @Test
    public void testLinkIntermediateEvent() throws Exception {
        ProcessInstance processInstance = ksession
                .startProcess("IntermediateLinkEvent");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testLinkEventCompositeProcess() throws Exception {
        ProcessInstance processInstance = ksession
                .startProcess("LinkEventCompositeProcess");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testIntermediateCatchEventConditionFilterByProcessInstance()
            throws Exception {
        Map<String, Object> params1 = new HashMap<String, Object>();
        params1.put("personId", Long.valueOf(1L));
        Person person1 = new Person();
        person1.setId(1L);
        WorkflowProcessInstance pi1 = (WorkflowProcessInstance) ksession
                .createProcessInstance(
                        "IntermediateCatchEventConditionFilterByProcessInstance",
                        params1);
        long pi1id = pi1.getId();

        ksession.insert(pi1);
        FactHandle personHandle1 = ksession.insert(person1);

        ksession.startProcessInstance(pi1.getId());

        Map<String, Object> params2 = new HashMap<String, Object>();
        params2.put("personId", Long.valueOf(2L));
        Person person2 = new Person();
        person2.setId(2L);

        WorkflowProcessInstance pi2 = (WorkflowProcessInstance) ksession
                .createProcessInstance(
                        "IntermediateCatchEventConditionFilterByProcessInstance",
                        params2);
        long pi2id = pi2.getId();

        ksession.insert(pi2);
        FactHandle personHandle2 = ksession.insert(person2);

        ksession.startProcessInstance(pi2.getId());

        person1.setName("John");
        ksession.update(personHandle1, person1);

        assertNull("First process should be completed",
                ksession.getProcessInstance(pi1id));
        assertNotNull("Second process should NOT be completed",
                ksession.getProcessInstance(pi2id));

    }

    @Test
    @RequirePersistence(false)
    public void testConditionalBoundaryEventOnTask() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new TestWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("BoundaryConditionalEventOnTask");

        Person person = new Person();
        person.setName("john");
        ksession.insert(person);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertNodeTriggered(processInstance.getId(), "StartProcess",
                "User Task", "Boundary event", "Condition met", "End2");
    }

    @Test
    @RequirePersistence(false)
    public void testConditionalBoundaryEventOnTaskComplete() throws Exception {
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                handler);
        ProcessInstance processInstance = ksession
                .startProcess("BoundarySignalOnTask");

        ksession.getWorkItemManager().completeWorkItem(
                handler.getWorkItem().getId(), null);
        Person person = new Person();
        person.setName("john");
        // as the node that boundary event is attached to has been completed insert will not have any effect
        ksession.insert(person);
        ksession.getWorkItemManager().completeWorkItem(
                handler.getWorkItem().getId(), null);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertNodeTriggered(processInstance.getId(), "StartProcess",
                "User Task", "User Task2", "End1");
    }

    @Test
    @RequirePersistence(false)
    public void testConditionalBoundaryEventOnTaskActiveOnStartup()
            throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new TestWorkItemHandler());

        Person person = new Person();
        person.setName("john");
        ksession.insert(person);
        ProcessInstance processInstance = ksession
                .startProcess("BoundaryConditionalEventOnTask");

        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertNodeTriggered(processInstance.getId(), "StartProcess",
                "User Task", "Boundary event", "Condition met", "End2");
    }

    @Test
    @RequirePersistence(false)
    public void testConditionalBoundaryEventInterrupting() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("MyTask",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("ConditionalBoundaryEvent");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        System.out.println(((ProcessInstanceImpl) processInstance)
                .getKnowledgeRuntime());

        ksession = restoreSession(ksession, true);
        Person person = new Person();
        person.setName("john");
        ksession.insert(person);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertNodeTriggered(processInstance.getId(), "StartProcess", "Hello",
                "StartSubProcess", "Task", "BoundaryEvent", "Goodbye",
                "EndProcess");
    }

    @Test
    @RequirePersistence(false)
    public void testMessageBoundaryEventOnTask() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new TestWorkItemHandler());

        ProcessInstance processInstance = ksession
                .startProcess("BoundaryMessageOnTask");
        ksession.signalEvent("Message-HelloMessage", "message data");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertNodeTriggered(processInstance.getId(), "StartProcess",
                "User Task", "Boundary event", "Condition met", "End2");
    }

    @Test
    @RequirePersistence(false)
    public void testMessageBoundaryEventOnTaskComplete() throws Exception {
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                handler);

        ProcessInstance processInstance = ksession
                .startProcess("BoundaryMessageOnTask");
        ksession.getWorkItemManager().completeWorkItem(
                handler.getWorkItem().getId(), null);
        ksession.signalEvent("Message-HelloMessage", "message data");
        ksession.getWorkItemManager().completeWorkItem(
                handler.getWorkItem().getId(), null);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertNodeTriggered(processInstance.getId(), "StartProcess",
                "User Task", "User Task2", "End1");
    }

    @Test
    @RequirePersistence(value = false)
    public void testIntermediateCatchEventTimerCycleWithError()
            throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("IntermediateCatchEventTimerCycleWithError");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        // now wait for 1 second for timer to trigger
        Thread.sleep(1000);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        ((WorkflowProcessInstance) ksession.getProcessInstance(processInstance
                .getId())).setVariable("x", new Integer(0));
        Thread.sleep(1000);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        Thread.sleep(1000);
        assertProcessInstanceActive(processInstance.getId(), ksession);

        Integer xValue = (Integer) ((WorkflowProcessInstance) processInstance)
                .getVariable("x");
        assertEquals(new Integer(2), xValue);

        ksession.abortProcessInstance(processInstance.getId());
        assertProcessInstanceAborted(processInstance.getId(), ksession);
    }

    @Test
    @RequirePersistence(true)
    public void testIntermediateCatchEventTimerCycleWithErrorPersistence()
            throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        ProcessInstance processInstance = ksession
                .startProcess("IntermediateCatchEventTimerCycleWithError");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        // now wait for 1 second for timer to trigger
        Thread.sleep(1000);
        assertProcessInstanceActive(processInstance.getId(), ksession);

        final long piId = processInstance.getId();
        ksession.execute(new GenericCommand<Void>() {
            public Void execute(Context context) {
                StatefulKnowledgeSession ksession = (StatefulKnowledgeSession) ((KnowledgeCommandContext) context)
                        .getKieSession();
                WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession
                        .getProcessInstance(piId);
                processInstance.setVariable("x", 0);
                return null;
            }
        });

        Thread.sleep(1000);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        Thread.sleep(1000);
        assertProcessInstanceActive(processInstance.getId(), ksession);

        Integer xValue = ksession.execute(new GenericCommand<Integer>() {
            public Integer execute(Context context) {
                StatefulKnowledgeSession ksession = (StatefulKnowledgeSession) ((KnowledgeCommandContext) context)
                        .getKieSession();
                WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession
                        .getProcessInstance(piId);
                return (Integer) processInstance.getVariable("x");

            }
        });
        assertEquals(new Integer(2), xValue);
        ksession.abortProcessInstance(processInstance.getId());
        assertProcessInstanceAborted(processInstance.getId(), ksession);
    }

    @Test
    public void testEventSubprocessSignal() throws Exception {
        final List<Long> executednodes = new ArrayList<Long>();
        ProcessEventListener listener = new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName()
                        .equals("Script Task 1")) {
                    executednodes.add(event.getNodeInstance().getId());
                }
            }

        };
        ksession.addEventListener(listener);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("EventSubprocessSignal");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.addEventListener(listener);

        ksession.signalEvent("MySignal", null, processInstance.getId());
        assertProcessInstanceActive(processInstance.getId(), ksession);
        ksession.signalEvent("MySignal", null);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        ksession.signalEvent("MySignal", null);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        ksession.signalEvent("MySignal", null);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertNodeTriggered(processInstance.getId(), "start", "User Task 1",
                "end", "Sub Process 1", "start-sub", "Script Task 1", "end-sub");
        assertEquals(4, executednodes.size());
    }

    @Test
    public void testEventSubprocessSignalWithStateNode() throws Exception {
        final List<Long> executednodes = new ArrayList<Long>();
        ProcessEventListener listener = new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName().equals("User Task 2")) {
                    executednodes.add(event.getNodeInstance().getId());
                }
            }

        };
        ksession.addEventListener(listener);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("EventSubprocessSignalWithStateNode");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.addEventListener(listener);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);

        WorkItem workItemTopProcess = workItemHandler.getWorkItem();

        ksession.signalEvent("MySignal", null, processInstance.getId());
        assertProcessInstanceActive(processInstance.getId(), ksession);
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);

        ksession.signalEvent("MySignal", null);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);

        ksession.signalEvent("MySignal", null);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);

        ksession.signalEvent("MySignal", null);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);

        assertNotNull(workItemTopProcess);
        ksession.getWorkItemManager().completeWorkItem(
                workItemTopProcess.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertNodeTriggered(processInstance.getId(), "start", "User Task 1",
                "end", "Sub Process 1", "start-sub", "User Task 2", "end-sub");
        assertEquals(4, executednodes.size());
    }

    @Test
    public void testEventSubprocessSignalInterrupting() throws Exception {
        final List<Long> executednodes = new ArrayList<Long>();
        ProcessEventListener listener = new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName()
                        .equals("Script Task 1")) {
                    executednodes.add(event.getNodeInstance().getId());
                }
            }

        };
        ksession.addEventListener(listener);

        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("EventSubprocessSignalInterrupting");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.addEventListener(listener);

        ksession.signalEvent("MySignal", null, processInstance.getId());

        assertProcessInstanceAborted(processInstance.getId(), ksession);
        assertNodeTriggered(processInstance.getId(), "start", "User Task 1",
                "Sub Process 1", "start-sub", "Script Task 1", "end-sub");
        assertEquals(1, executednodes.size());
    }

    @Test
    public void testEventSubprocessMessage() throws Exception {
        final List<Long> executednodes = new ArrayList<Long>();
        ProcessEventListener listener = new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName()
                        .equals("Script Task 1")) {
                    executednodes.add(event.getNodeInstance().getId());
                }
            }

        };
        ksession.addEventListener(listener);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("EventSubprocessMessage");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.addEventListener(listener);

        ksession.signalEvent("Message-HelloMessage", null,
                processInstance.getId());
        ksession.signalEvent("Message-HelloMessage", null);
        ksession.signalEvent("Message-HelloMessage", null);
        ksession.signalEvent("Message-HelloMessage", null);
        ksession.getProcessInstance(processInstance.getId());
        ksession.getProcessInstance(processInstance.getId());
        ksession.getProcessInstance(processInstance.getId());
        ksession.getProcessInstance(processInstance.getId());
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertNodeTriggered(processInstance.getId(), "start", "User Task 1",
                "end", "Sub Process 1", "start-sub", "Script Task 1", "end-sub");
        assertEquals(4, executednodes.size());
    }

    @Test
    public void testEventSubprocessEscalation() throws Exception {
        final List<Long> executednodes = new ArrayList<Long>();
        ProcessEventListener listener = new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName()
                        .equals("Script Task 1")) {
                    executednodes.add(event.getNodeInstance().getId());
                }
            }

        };
        ksession.addEventListener(listener);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("EventSubprocessEscalation");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.addEventListener(listener);

        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertNodeTriggered(processInstance.getId(), "start", "User Task 1",
                "end", "Sub Process 1", "start-sub", "Script Task 1", "end-sub");
        assertEquals(1, executednodes.size());
    }

    @Test
    public void testEventSubprocessError() throws Exception {
        final List<Long> executednodes = new ArrayList<Long>();
        ProcessEventListener listener = new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName()
                        .equals("Script Task 1")) {
                    executednodes.add(event.getNodeInstance().getId());
                }
            }

        };
        ksession.addEventListener(listener);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("EventSubprocessError");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.addEventListener(listener);

        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertNodeTriggered(processInstance.getId(), "start", "User Task 1",
                "end", "Sub Process 1", "start-sub", "Script Task 1", "end-sub");
        assertEquals(1, executednodes.size());
    }

    @Test
    public void testEventSubprocessCompensation() throws Exception {
        final List<Long> executednodes = new ArrayList<Long>();
        ProcessEventListener listener = new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName()
                        .equals("Script Task 1")) {
                    executednodes.add(event.getNodeInstance().getId());
                }
            }

        };
        ksession.addEventListener(listener);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("EventSubprocessCompensation");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.addEventListener(listener);

        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertNodeTriggered(processInstance.getId(), "start", "User Task 1",
                "end", "Sub Process 1", "start-sub", "Script Task 1", "end-sub");
        assertEquals(1, executednodes.size());
    }

    @Test
    public void testEventSubprocessTimer() throws Exception {
        final List<Long> executednodes = new ArrayList<Long>();
        ProcessEventListener listener = new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName()
                        .equals("Script Task 1")) {
                    executednodes.add(event.getNodeInstance().getId());
                }
            }

        };
        ksession.addEventListener(listener);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("EventSubprocessTimer");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(1000);

        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertNodeTriggered(processInstance.getId(), "start", "User Task 1",
                "end", "Sub Process 1", "start-sub", "Script Task 1", "end-sub");
        assertEquals(1, executednodes.size());
    }

    @Test
    public void testEventSubprocessTimerCycle() throws Exception {
        final List<Long> executednodes = new ArrayList<Long>();
        ProcessEventListener listener = new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName()
                        .equals("Script Task 1")) {
                    executednodes.add(event.getNodeInstance().getId());
                }
            }

        };
        ksession.addEventListener(listener);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("EventSubprocessTimerCycle");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        Thread.sleep(2000);

        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertNodeTriggered(processInstance.getId(), "start", "User Task 1",
                "end", "start-sub", "Script Task 1", "end-sub");
        assertEquals(4, executednodes.size());
    }

    @Test
    public void testEventSubprocessConditional() throws Exception {
        final List<Long> executednodes = new ArrayList<Long>();
        ProcessEventListener listener = new DefaultProcessEventListener() {

            @Override
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                if (event.getNodeInstance().getNodeName()
                        .equals("Script Task 1")) {
                    executednodes.add(event.getNodeInstance().getId());
                }
            }

        };
        ksession.addEventListener(listener);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("EventSubprocessConditional");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);

        Person person = new Person();
        person.setName("john");
        ksession.insert(person);

        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertNodeTriggered(processInstance.getId(), "start", "User Task 1",
                "end", "Sub Process 1", "start-sub", "Script Task 1", "end-sub");
        assertEquals(1, executednodes.size());
    }
}
