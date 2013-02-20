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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.drools.WorkingMemory;
import org.drools.event.ActivationCancelledEvent;
import org.drools.event.ActivationCreatedEvent;
import org.drools.event.AfterActivationFiredEvent;
import org.drools.event.AgendaGroupPoppedEvent;
import org.drools.event.AgendaGroupPushedEvent;
import org.drools.event.BeforeActivationFiredEvent;
import org.drools.event.RuleFlowGroupActivatedEvent;
import org.drools.event.RuleFlowGroupDeactivatedEvent;
import org.drools.impl.StatefulKnowledgeSessionImpl;
import org.jbpm.bpmn2.handler.ReceiveTaskHandler;
import org.jbpm.bpmn2.handler.SendTaskHandler;
import org.jbpm.bpmn2.handler.ServiceTaskHandler;
import org.jbpm.bpmn2.objects.Person;
import org.jbpm.process.instance.impl.RuleAwareProcessEventLister;
import org.jbpm.process.instance.impl.demo.DoNothingWorkItemHandler;
import org.jbpm.process.instance.impl.demo.SystemOutWorkItemHandler;
import org.jbpm.test.JbpmJUnitTestCase;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.jbpm.workflow.instance.node.CompositeContextNodeInstance;
import org.jbpm.workflow.instance.node.DynamicNodeInstance;
import org.jbpm.workflow.instance.node.DynamicUtils;
import org.jbpm.workflow.instance.node.ForEachNodeInstance;
import org.jbpm.workflow.instance.node.ForEachNodeInstance.ForEachJoinNodeInstance;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.KieBase;
import org.kie.cdi.KBase;
import org.kie.event.process.DefaultProcessEventListener;
import org.kie.event.process.ProcessNodeTriggeredEvent;
import org.kie.event.process.ProcessStartedEvent;
import org.kie.event.process.ProcessVariableChangedEvent;
import org.kie.runtime.StatefulKnowledgeSession;
import org.kie.runtime.process.NodeInstance;
import org.kie.runtime.process.ProcessInstance;
import org.kie.runtime.process.WorkItem;
import org.kie.runtime.process.WorkflowProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(CDITestRunner.class)
public class ActivityTest extends JbpmJUnitTestCase {
    
    @Inject
    @KBase("activity")
    private KieBase activityBase;

    private StatefulKnowledgeSession ksession;
    private StatefulKnowledgeSession ksession2;

    private Logger logger = LoggerFactory.getLogger(ActivityTest.class);

    public ActivityTest() {

    }
    
    @BeforeClass
    public static void setup() throws Exception {
        if (PERSISTENCE) {
            setUpDataSource();
        }
    }

    @Before
    public void init() throws Exception {
        String [] testFailsWithPersistence = { 
            // broken, but should work?!?
            "testBusinessRuleTask",
            "testNullVariableInScriptTaskProcess",
            "testCallActivityWithBoundaryEvent",
            "testRuleTaskWithFacts",
            "testMultiInstanceLoopCharacteristicsProcessWithORGateway"
        };
        boolean persistence = PERSISTENCE;
        for( String testNameBegin : testFailsWithPersistence ) { 
             if( testName.getMethodName().startsWith(testNameBegin) ) { 
                 persistence = false;
             }
        }
        setPersistence(persistence);
        ksession = createKnowledgeSession(activityBase);
    }

    @After
    public void dispose() {
        ksession.dispose();
        if (ksession2 != null) {
            ksession2.dispose();
            ksession2 = null;
        }
    }

    @Test
    public void testMinimalProcess() throws Exception {
        ProcessInstance processInstance = ksession.startProcess("Minimal");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMinimalProcessImplicit() throws Exception {
        ProcessInstance processInstance = ksession
                .startProcess("MinimalImplicit");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMinimalProcessWithGraphical() throws Exception {
        ProcessInstance processInstance = ksession
                .startProcess("MinimalWithGraphical");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMinimalProcessWithDIGraphical() throws Exception {
        ProcessInstance processInstance = ksession
                .startProcess("MinimalWithDIGraphical");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testCompositeProcessWithDIGraphical() throws Exception {
        ProcessInstance processInstance = ksession
                .startProcess("CompositeWithDIGraphical");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testScriptTask() throws Exception {
        ProcessInstance processInstance = ksession.startProcess("ScriptTask");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testSubProcess() throws Exception {
        ksession.addEventListener(new DefaultProcessEventListener() {
            public void afterProcessStarted(ProcessStartedEvent event) {
                logger.debug(event.toString());
            }

            public void beforeVariableChanged(ProcessVariableChangedEvent event) {
                logger.debug(event.toString());
            }

            public void afterVariableChanged(ProcessVariableChangedEvent event) {
                logger.debug(event.toString());
            }
        });
        ProcessInstance processInstance = ksession.startProcess("SubProcess");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testSubProcessWithTerminateEndEvent() throws Exception {
        final List<String> list = new ArrayList<String>();
        ksession.addEventListener(new DefaultProcessEventListener() {

            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                list.add(event.getNodeInstance().getNodeName());
            }
        });
        ProcessInstance processInstance = ksession
                .startProcess("SubProcessTerminate");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertEquals(7, list.size());
    }

    @Test
    public void testSubProcessWithTerminateEndEventProcessScope()
            throws Exception {
        final List<String> list = new ArrayList<String>();
        ksession.addEventListener(new DefaultProcessEventListener() {

            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                list.add(event.getNodeInstance().getNodeName());
            }
        });
        ProcessInstance processInstance = ksession
                .startProcess("SubProcessTerminateScope");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertEquals(5, list.size());
    }

    @Test
    public void testCallActivity() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "oldValue");
        ProcessInstance processInstance = ksession.startProcess("CallActivity",
                params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertEquals("new value",
                ((WorkflowProcessInstance) processInstance).getVariable("y"));
    }

    @Test
    public void testCallActivityByName() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "oldValue");
        ProcessInstance processInstance = ksession.startProcess(
                "CallActivityByName", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertEquals("new value V2",
                ((WorkflowProcessInstance) processInstance).getVariable("y"));
    }

    @Test
    public void testCallActivityWithHistoryLog() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "oldValue");
        ProcessInstance processInstance = ksession.startProcess(
                "CallActivity", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertEquals("new value",
                ((WorkflowProcessInstance) processInstance).getVariable("y"));
//        enable this as soon as pull request #98 is in as it fixes subprocess instance creation          
//        List<ProcessInstanceLog> subprocesses = JPAProcessInstanceDbLog.findSubProcessInstances(processInstance.getId());
//        assertNotNull(subprocesses);
//        assertEquals(1, subprocesses.size());
    }

    @Test
    public void testRuleTask() throws Exception {
        List<String> list = new ArrayList<String>();
        ksession.setGlobal("list", list);
        ProcessInstance processInstance = ksession.startProcess("RuleTask");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        restoreSession(ksession, true);
        ksession.fireAllRules();
        assertTrue(list.size() == 1);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testRuleTask2() throws Exception {
        List<String> list = new ArrayList<String>();
        ksession.setGlobal("list", list);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "SomeString");
        ProcessInstance processInstance = ksession.startProcess("RuleTask2",
                params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        restoreSession(ksession, true);
        ksession.fireAllRules();
        assertTrue(list.size() == 0);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testRuleTaskWithFacts() throws Exception {

        final org.drools.event.AgendaEventListener agendaEventListener = new org.drools.event.AgendaEventListener() {
            public void activationCreated(ActivationCreatedEvent event,
                    WorkingMemory workingMemory) {
                ksession.fireAllRules();
            }

            public void activationCancelled(ActivationCancelledEvent event,
                    WorkingMemory workingMemory) {
            }

            public void beforeActivationFired(BeforeActivationFiredEvent event,
                    WorkingMemory workingMemory) {
            }

            public void afterActivationFired(AfterActivationFiredEvent event,
                    WorkingMemory workingMemory) {
            }

            public void agendaGroupPopped(AgendaGroupPoppedEvent event,
                    WorkingMemory workingMemory) {
            }

            public void agendaGroupPushed(AgendaGroupPushedEvent event,
                    WorkingMemory workingMemory) {
            }

            public void beforeRuleFlowGroupActivated(
                    RuleFlowGroupActivatedEvent event,
                    WorkingMemory workingMemory) {
            }

            public void afterRuleFlowGroupActivated(
                    RuleFlowGroupActivatedEvent event,
                    WorkingMemory workingMemory) {
                workingMemory.fireAllRules();
            }

            public void beforeRuleFlowGroupDeactivated(
                    RuleFlowGroupDeactivatedEvent event,
                    WorkingMemory workingMemory) {
            }

            public void afterRuleFlowGroupDeactivated(
                    RuleFlowGroupDeactivatedEvent event,
                    WorkingMemory workingMemory) {
            }
        };
        ((StatefulKnowledgeSessionImpl) ksession).session
                .addEventListener(agendaEventListener); // FIXME org.drools.command.impl.CommandBasedStatefulKnowledgeSession cannot be cast to org.drools.impl.StatefulKnowledgeSessionImpl while run with persistence

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "SomeString");
        ProcessInstance processInstance = ksession.startProcess(
                "RuleTaskWithFact", params);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);

        params = new HashMap<String, Object>();

        try {
            processInstance = ksession.startProcess("RuleTaskWithFact", params);

            fail("Should fail");
        } catch (Exception e) {
            // e.printStackTrace();
            System.out.println("Expected exception " + e);
        }

        params = new HashMap<String, Object>();
        params.put("x", "SomeString");
        processInstance = ksession.startProcess("RuleTaskWithFact", params);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testRuleTaskAcrossSessions() throws Exception {
        ksession2 = createKnowledgeSession(activityBase);
        List<String> list1 = new ArrayList<String>();
        ksession.setGlobal("list", list1);
        List<String> list2 = new ArrayList<String>();
        ksession2.setGlobal("list", list2);
        ProcessInstance processInstance1 = ksession.startProcess("RuleTask");
        ProcessInstance processInstance2 = ksession2.startProcess("RuleTask");
        ksession.fireAllRules();
        assertProcessInstanceCompleted(processInstance1.getId(), ksession);
        assertProcessInstanceActive(processInstance2.getId(), ksession2);
        ksession2.fireAllRules();
        assertProcessInstanceCompleted(processInstance2.getId(), ksession2);
    }

    @Test
    public void testUserTask() throws Exception {
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession.startProcess("UserTask");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        assertEquals("john", workItem.getParameter("ActorId"));
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsProcess() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        List<String> myList = new ArrayList<String>();
        myList.add("First Item");
        myList.add("Second Item");
        params.put("list", myList);
        ProcessInstance processInstance = ksession.startProcess(
                "MultiInstanceLoopCharacteristicsProcess", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsProcessWithOutput()
            throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        List<String> myList = new ArrayList<String>();
        List<String> myListOut = new ArrayList<String>();
        myList.add("First Item");
        myList.add("Second Item");
        params.put("list", myList);
        params.put("listOut", myListOut);
        assertEquals(0, myListOut.size());
        ProcessInstance processInstance = ksession.startProcess(
                "MultiInstanceLoopCharacteristicsProcessWithOutput", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertEquals(2, myListOut.size());
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsProcessWithORGateway()
            throws Exception {
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        Map<String, Object> params = new HashMap<String, Object>();
        List<Integer> myList = new ArrayList<Integer>();
        myList.add(12);
        myList.add(15);
        params.put("list", myList);
        ProcessInstance processInstance = ksession.startProcess(
                "MultiInstanceLoopCharacteristicsProcessWithORGateway", params);

        List<WorkItem> workItems = workItemHandler.getWorkItems();
        assertEquals(4, workItems.size());

        Collection<NodeInstance> nodeInstances = ((WorkflowProcessInstanceImpl) processInstance)
                .getNodeInstances();
        assertEquals(1, nodeInstances.size());
        NodeInstance nodeInstance = nodeInstances.iterator().next();
        assertTrue(nodeInstance instanceof ForEachNodeInstance);

        Collection<NodeInstance> nodeInstancesChild = ((ForEachNodeInstance) nodeInstance)
                .getNodeInstances();
        assertEquals(2, nodeInstancesChild.size());

        for (NodeInstance child : nodeInstancesChild) {
            assertTrue(child instanceof CompositeContextNodeInstance);
            assertEquals(2, ((CompositeContextNodeInstance) child)
                    .getNodeInstances().size());
        }

        ksession.getWorkItemManager().completeWorkItem(
                workItems.get(0).getId(), null);
        ksession.getWorkItemManager().completeWorkItem(
                workItems.get(1).getId(), null);

        nodeInstances = ((WorkflowProcessInstanceImpl) processInstance)
                .getNodeInstances();
        assertEquals(1, nodeInstances.size());
        nodeInstance = nodeInstances.iterator().next();
        assertTrue(nodeInstance instanceof ForEachNodeInstance);

        nodeInstancesChild = ((ForEachNodeInstance) nodeInstance)
                .getNodeInstances();
        assertEquals(2, nodeInstancesChild.size());

        Iterator<NodeInstance> childIterator = nodeInstancesChild.iterator();

        NodeInstance ni1 = childIterator.next();
        System.out.println(ni1.getClass());
        NodeInstance ni2 = childIterator.next();
        System.out.println(ni2.getClass()); 
        assertTrue(ni1 instanceof CompositeContextNodeInstance);
        assertTrue(ni2 instanceof ForEachJoinNodeInstance);

        ksession.getWorkItemManager().completeWorkItem(
                workItems.get(2).getId(), null);
        ksession.getWorkItemManager().completeWorkItem(
                workItems.get(3).getId(), null);

        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsTaskWithOutput()
            throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new SystemOutWorkItemHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        List<String> myList = new ArrayList<String>();
        List<String> myListOut = new ArrayList<String>();
        myList.add("First Item");
        myList.add("Second Item");
        params.put("list", myList);
        params.put("listOut", myListOut);
        assertEquals(0, myListOut.size());
        ProcessInstance processInstance = ksession.startProcess(
                "MultiInstanceLoopCharacteristicsTaskWithOutput", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertEquals(2, myListOut.size());
    }

    @Test
    public void testMultiInstanceLoopCharacteristicsTask() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new SystemOutWorkItemHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        List<String> myList = new ArrayList<String>();
        myList.add("First Item");
        myList.add("Second Item");
        params.put("list", myList);
        ProcessInstance processInstance = ksession.startProcess(
                "MultiInstanceLoopCharacteristicsTask", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testAdHocSubProcess() throws Exception {
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("AdHocSubProcess");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNull(workItem);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ksession.fireAllRules();
        logger.debug("Signaling Hello2");
        ksession.signalEvent("Hello2", null, processInstance.getId());
        workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
    }

    @Test
    public void testAdHocSubProcessAutoComplete() throws Exception {
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("AdHocSubProcessAutoComplete");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNull(workItem);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ksession.fireAllRules();
        workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testAdHocSubProcessAutoCompleteDynamicTask() throws Exception {
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        TestWorkItemHandler workItemHandler2 = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("OtherTask",
                workItemHandler2);
        ProcessInstance processInstance = ksession
                .startProcess("AdHocSubProcessAutoComplete");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        DynamicNodeInstance dynamicContext = (DynamicNodeInstance) ((WorkflowProcessInstance) processInstance)
                .getNodeInstances().iterator().next();
        DynamicUtils.addDynamicWorkItem(dynamicContext, ksession, "OtherTask",
                new HashMap<String, Object>());
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNull(workItem);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ksession.fireAllRules();
        workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        workItem = workItemHandler2.getWorkItem();
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testAdHocSubProcessAutoCompleteDynamicSubProcess()
            throws Exception {
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        TestWorkItemHandler workItemHandler2 = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("OtherTask",
                workItemHandler2);
        ProcessInstance processInstance = ksession
                .startProcess("AdHocSubProcessAutoComplete");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession.fireAllRules();
        DynamicNodeInstance dynamicContext = (DynamicNodeInstance) ((WorkflowProcessInstance) processInstance)
                .getNodeInstances().iterator().next();
        DynamicUtils.addDynamicSubProcess(dynamicContext, ksession, "Minimal",
                new HashMap<String, Object>());
        ksession = restoreSession(ksession, true);
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        // assertProcessInstanceActive(processInstance.getId(), ksession);
        // workItem = workItemHandler2.getWorkItem();
        // ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testAdHocSubProcessAutoCompleteDynamicSubProcess2()
            throws Exception {
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        TestWorkItemHandler workItemHandler2 = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Service Task",
                workItemHandler2);
        ProcessInstance processInstance = ksession
                .startProcess("AdHocSubProcessAutoComplete");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession.fireAllRules();
        DynamicNodeInstance dynamicContext = (DynamicNodeInstance) ((WorkflowProcessInstance) processInstance)
                .getNodeInstances().iterator().next();
        DynamicUtils.addDynamicSubProcess(dynamicContext, ksession,
                "ServiceProcess", new HashMap<String, Object>());
        ksession = restoreSession(ksession, true);
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceActive(processInstance.getId(), ksession);
        workItem = workItemHandler2.getWorkItem();
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testAdHocProcess() throws Exception {
        ProcessInstance processInstance = ksession.startProcess("AdHocProcess");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        logger.debug("Triggering node");
        ksession.signalEvent("Task1", null, processInstance.getId());
        assertProcessInstanceActive(processInstance.getId(), ksession);
        ksession.signalEvent("User1", null, processInstance.getId());
        assertProcessInstanceActive(processInstance.getId(), ksession);
        ksession.insert(new Person());
        ksession.signalEvent("Task3", null, processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testAdHocProcessDynamicTask() throws Exception {
        ProcessInstance processInstance = ksession.startProcess("AdHocProcess");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        logger.debug("Triggering node");
        ksession.signalEvent("Task1", null, processInstance.getId());
        assertProcessInstanceActive(processInstance.getId(), ksession);
        TestWorkItemHandler workItemHandler2 = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("OtherTask",
                workItemHandler2);
        DynamicUtils.addDynamicWorkItem(processInstance, ksession, "OtherTask",
                new HashMap<String, Object>());
        WorkItem workItem = workItemHandler2.getWorkItem();
        assertNotNull(workItem);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        ksession.signalEvent("User1", null, processInstance.getId());
        assertProcessInstanceActive(processInstance.getId(), ksession);
        ksession.insert(new Person());
        ksession.signalEvent("Task3", null, processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testAdHocProcessDynamicSubProcess() throws Exception {
        ProcessInstance processInstance = ksession.startProcess("AdHocProcess");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                new DoNothingWorkItemHandler());
        logger.debug("Triggering node");
        ksession.signalEvent("Task1", null, processInstance.getId());
        assertProcessInstanceActive(processInstance.getId(), ksession);
        TestWorkItemHandler workItemHandler2 = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("OtherTask",
                workItemHandler2);
        DynamicUtils.addDynamicSubProcess(processInstance, ksession, "Minimal",
                new HashMap<String, Object>());
        ksession = restoreSession(ksession, true);
        ksession.signalEvent("User1", null, processInstance.getId());
        assertProcessInstanceActive(processInstance.getId(), ksession);
        ksession.insert(new Person());
        ksession.signalEvent("Task3", null, processInstance.getId());
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testServiceTask() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Service Task",
                new ServiceTaskHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("s", "john");
        WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession
                .startProcess("ServiceProcess", params);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertEquals("Hello john!", processInstance.getVariable("s"));
    }

    @Test
    public void testSendTask() throws Exception {
        ksession.getWorkItemManager().registerWorkItemHandler("Send Task",
                new SendTaskHandler());
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("s", "john");
        WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession
                .startProcess("SendTask", params);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testReceiveTask() throws Exception {
        ReceiveTaskHandler receiveTaskHandler = new ReceiveTaskHandler(ksession);
        ksession.getWorkItemManager().registerWorkItemHandler("Receive Task",
                receiveTaskHandler);
        WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession
                .startProcess("ReceiveTask");
        assertEquals(ProcessInstance.STATE_ACTIVE, processInstance.getState());
        ksession = restoreSession(ksession, true);
        receiveTaskHandler.messageReceived("HelloMessage", "Hello john!");
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testBusinessRuleTask() throws Exception {
        ksession.addEventListener(new RuleAwareProcessEventLister());
        ProcessInstance processInstance = ksession
                .startProcess("BusinessRuleTask");

        int fired = ksession.fireAllRules();
        assertEquals(1, fired);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testBusinessRuleTaskDynamic() throws Exception {
        ksession.addEventListener(new RuleAwareProcessEventLister());

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("dynamicrule", "BusinessRules");
        ProcessInstance processInstance = ksession.startProcess(
                "BusinessRuleTaskDynamic", params);

        int fired = ksession.fireAllRules();
        assertEquals(1, fired);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testBusinessRuleTaskWithDataInputs() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("person", new Person());
        ProcessInstance processInstance = ksession.startProcess(
                "BusinessRuleTaskWithDataInputs", params);

        int fired = ksession.fireAllRules();
        assertEquals(1, fired);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testNullVariableInScriptTaskProcess() throws Exception {
        ProcessInstance process = ksession
                .startProcess("nullVariableInScriptAfterTimer");

        assertEquals(ProcessInstance.STATE_ACTIVE, process.getState());

        long sleep = 1000;
        logger.debug("Sleeping " + sleep / 1000 + " seconds.");
        Thread.sleep(sleep);

        assertTrue(ProcessInstance.STATE_ABORTED == process.getState());
    }

    @Test
    public void testScriptTaskWithVariableByName() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("myVar", "test");
        ProcessInstance processInstance = ksession.startProcess(
                "ProcessWithVariableName", params);
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testCallActivityWithBoundaryEvent() throws Exception {
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "oldValue");
        ProcessInstance processInstance = ksession.startProcess(
                "CallActivityWithBoundaryEvent", params);

        Thread.sleep(3000);

        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
        assertEquals("new timer value",
                ((WorkflowProcessInstance) processInstance).getVariable("y"));
        assertNodeTriggered(processInstance.getId(), "StartProcess",
                "CallActivity", "Boundary event", "Script Task", "end",
                "StartProcess2", "User Task");
    }

    @Test
    public void testUserTaskWithBooleanOutput() throws Exception {
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("UserTaskWithBooleanOutput");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        assertEquals("john", workItem.getParameter("ActorId"));
        HashMap<String, Object> output = new HashMap<String, Object>();
        output.put("isCheckedCheckbox", "true");
        ksession.getWorkItemManager()
                .completeWorkItem(workItem.getId(), output);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

    @Test
    public void testUserTaskWithSimData() throws Exception {
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        ProcessInstance processInstance = ksession
                .startProcess("UserTaskWithSimulationMetaData");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_ACTIVE);
        ksession = restoreSession(ksession, true);
        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        assertEquals("john", workItem.getParameter("ActorId"));
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

}
