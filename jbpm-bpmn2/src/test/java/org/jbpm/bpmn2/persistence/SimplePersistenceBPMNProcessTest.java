package org.jbpm.bpmn2.persistence;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.drools.WorkingMemory;
import org.drools.command.impl.CommandBasedStatefulKnowledgeSession;
import org.drools.command.impl.GenericCommand;
import org.drools.command.impl.KnowledgeCommandContext;
import org.drools.compiler.PackageBuilderConfiguration;
import org.drools.event.ActivationCancelledEvent;
import org.drools.event.ActivationCreatedEvent;
import org.drools.event.AfterActivationFiredEvent;
import org.drools.event.AgendaGroupPoppedEvent;
import org.drools.event.AgendaGroupPushedEvent;
import org.drools.event.BeforeActivationFiredEvent;
import org.drools.event.RuleFlowGroupActivatedEvent;
import org.drools.event.RuleFlowGroupDeactivatedEvent;
import org.drools.impl.EnvironmentFactory;
import org.drools.impl.StatefulKnowledgeSessionImpl;
import org.jbpm.bpmn2.JbpmBpmn2TestCase;
import org.jbpm.bpmn2.objects.Person;
import org.jbpm.bpmn2.xml.BPMNDISemanticModule;
import org.jbpm.bpmn2.xml.BPMNSemanticModule;
import org.jbpm.bpmn2.xml.XmlBPMNProcessDumper;
import org.jbpm.compiler.xml.XmlProcessReader;
import org.jbpm.process.audit.JPAProcessInstanceDbLog;
import org.jbpm.process.audit.NodeInstanceLog;
import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.process.instance.impl.RuleAwareProcessEventLister;
import org.jbpm.process.instance.impl.demo.DoNothingWorkItemHandler;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.jbpm.workflow.instance.node.CompositeContextNodeInstance;
import org.jbpm.workflow.instance.node.ForEachNodeInstance;
import org.kie.KnowledgeBase;
import org.kie.KnowledgeBaseFactory;
import org.kie.builder.KnowledgeBuilder;
import org.kie.builder.KnowledgeBuilderConfiguration;
import org.kie.builder.KnowledgeBuilderFactory;
import org.kie.command.Context;
import org.kie.definition.process.Process;
import org.kie.event.process.DefaultProcessEventListener;
import org.kie.event.process.ProcessNodeLeftEvent;
import org.kie.event.process.ProcessNodeTriggeredEvent;
import org.kie.event.rule.DebugAgendaEventListener;
import org.kie.io.ResourceFactory;
import org.kie.io.ResourceType;
import org.kie.persistence.jpa.JPAKnowledgeService;
import org.kie.runtime.Environment;
import org.kie.runtime.EnvironmentName;
import org.kie.runtime.StatefulKnowledgeSession;
import org.kie.runtime.process.NodeInstance;
import org.kie.runtime.process.ProcessInstance;
import org.kie.runtime.process.WorkItem;
import org.kie.runtime.process.WorkflowProcessInstance;

public class SimplePersistenceBPMNProcessTest extends JbpmBpmn2TestCase {

    public SimplePersistenceBPMNProcessTest() {
        super(true);
    }
    
    public void testInclusiveSplitAndJoinNested() throws Exception {
        KnowledgeBase kbase = createKnowledgeBase("BPMN2-InclusiveSplitAndJoinNested.bpmn2");
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", 15);
        ProcessInstance processInstance = ksession.startProcess(
                "com.sample.test", params);
        
        List<WorkItem> activeWorkItems = workItemHandler.getWorkItems();
        
        assertEquals(2, activeWorkItems.size());
        restoreSession(ksession, true);
        
        for (WorkItem wi : activeWorkItems) {
            ksession.getWorkItemManager().completeWorkItem(wi.getId(), null);
        }
        
        activeWorkItems = workItemHandler.getWorkItems();
        assertEquals(2, activeWorkItems.size());
        restoreSession(ksession, true);
        
        for (WorkItem wi : activeWorkItems) {
            ksession.getWorkItemManager().completeWorkItem(wi.getId(), null);
        }
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }
    
    public void testInclusiveSplitAndJoinEmbedded() throws Exception {
        KnowledgeBase kbase = createKnowledgeBase("BPMN2-InclusiveSplitAndJoinEmbedded.bpmn2");
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", 15);
        ProcessInstance processInstance = ksession.startProcess(
                "com.sample.test", params);
        
        List<WorkItem> activeWorkItems = workItemHandler.getWorkItems();
        
        assertEquals(2, activeWorkItems.size());
        restoreSession(ksession, true);
        
        for (WorkItem wi : activeWorkItems) {
            ksession.getWorkItemManager().completeWorkItem(wi.getId(), null);
        }
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }
    
    public void testInclusiveSplitAndJoinWithParallel() throws Exception {
        KnowledgeBase kbase = createKnowledgeBase("BPMN2-InclusiveSplitAndJoinWithParallel.bpmn2");
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", 25);
        ProcessInstance processInstance = ksession.startProcess(
                "com.sample.test", params);
        
        List<WorkItem> activeWorkItems = workItemHandler.getWorkItems();
        
        assertEquals(4, activeWorkItems.size());
        restoreSession(ksession, true);
        
        for (WorkItem wi : activeWorkItems) {
            ksession.getWorkItemManager().completeWorkItem(wi.getId(), null);
        }
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }
    
    public void testInclusiveSplitAndJoinWithEnd() throws Exception {
        KnowledgeBase kbase = createKnowledgeBase("BPMN2-InclusiveSplitAndJoinWithEnd.bpmn2");
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", 25);
        ProcessInstance processInstance = ksession.startProcess(
                "com.sample.test", params);
        
        List<WorkItem> activeWorkItems = workItemHandler.getWorkItems();
        
        assertEquals(3, activeWorkItems.size());
        restoreSession(ksession, true);
        
        for (int i = 0; i < 2; i++) {
            ksession.getWorkItemManager().completeWorkItem(activeWorkItems.get(i).getId(), null);
        }
        assertProcessInstanceActive(processInstance.getId(), ksession);
        
        ksession.getWorkItemManager().completeWorkItem(activeWorkItems.get(2).getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }
    
    public void testInclusiveSplitAndJoinWithTimer() throws Exception {
        KnowledgeBase kbase = createKnowledgeBase("BPMN2-InclusiveSplitAndJoinWithTimer.bpmn2");
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", 15);
        ProcessInstance processInstance = ksession.startProcess(
                "com.sample.test", params);
 
        List<WorkItem> activeWorkItems = workItemHandler.getWorkItems();

        assertEquals(1, activeWorkItems.size());
        ksession.getWorkItemManager().completeWorkItem(activeWorkItems.get(0).getId(), null);
        ksession.fireAllRules();
        Thread.sleep(3000);
        assertProcessInstanceActive(processInstance.getId(), ksession);

        activeWorkItems = workItemHandler.getWorkItems();
        assertEquals(2, activeWorkItems.size());
        
        ksession.getWorkItemManager().completeWorkItem(activeWorkItems.get(0).getId(), null);
        assertProcessInstanceActive(processInstance.getId(), ksession);

        ksession.getWorkItemManager().completeWorkItem(activeWorkItems.get(1).getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }
    
    public void testInclusiveSplitAndJoinExtraPath() throws Exception {
        KnowledgeBase kbase = createKnowledgeBase("BPMN2-InclusiveSplitAndJoinExtraPath.bpmn2");
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", 25);
        ProcessInstance processInstance = ksession.startProcess(
                "com.sample.test", params);
        
        ksession.signalEvent("signal", null);
        
        List<WorkItem> activeWorkItems = workItemHandler.getWorkItems();
        
        assertEquals(4, activeWorkItems.size());
        restoreSession(ksession, true);
        
        for (int i = 0; i < 3; i++) {
            ksession.getWorkItemManager().completeWorkItem(activeWorkItems.get(i).getId(), null);
        }
        assertProcessInstanceActive(processInstance.getId(), ksession);

        ksession.getWorkItemManager().completeWorkItem(activeWorkItems.get(3).getId(), null);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }
    
    public void testMultiInstanceLoopCharacteristicsProcessWithORGateway() throws Exception {
        KnowledgeBase kbase = createKnowledgeBase("BPMN2-MultiInstanceLoopCharacteristicsProcessWithORgateway.bpmn2");
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task",
                workItemHandler);
        Map<String, Object> params = new HashMap<String, Object>();
        List<Integer> myList = new ArrayList<Integer>();
        myList.add(12);
        myList.add(15);
        params.put("list", myList);
        ProcessInstance processInstance = ksession.startProcess(
                "MultiInstanceLoopCharacteristicsProcess", params);
        
        List<WorkItem> workItems = workItemHandler.getWorkItems();
        assertEquals(4, workItems.size());
        
        Collection<NodeInstance> nodeInstances = ((WorkflowProcessInstanceImpl) processInstance).getNodeInstances();
        assertEquals(1, nodeInstances.size());
        NodeInstance nodeInstance = nodeInstances.iterator().next(); 
        assertTrue(nodeInstance instanceof ForEachNodeInstance);
        
        Collection<NodeInstance> nodeInstancesChild = ((ForEachNodeInstance) nodeInstance).getNodeInstances();
        assertEquals(2, nodeInstancesChild.size());
        
        for (NodeInstance child : nodeInstancesChild) {
            assertTrue(child instanceof CompositeContextNodeInstance);
            assertEquals(2, ((CompositeContextNodeInstance) child).getNodeInstances().size());
        }
        
        ksession.getWorkItemManager().completeWorkItem(workItems.get(0).getId(), null);
        ksession.getWorkItemManager().completeWorkItem(workItems.get(1).getId(), null);
        
        processInstance = ksession.getProcessInstance(processInstance.getId());
        
        nodeInstances = ((WorkflowProcessInstanceImpl) processInstance).getNodeInstances();
        assertEquals(1, nodeInstances.size());
        nodeInstance = nodeInstances.iterator().next(); 
        assertTrue(nodeInstance instanceof ForEachNodeInstance);
        
        nodeInstancesChild = ((ForEachNodeInstance) nodeInstance).getNodeInstances();
        assertEquals(1, nodeInstancesChild.size());
        
        Iterator<NodeInstance> childIterator = nodeInstancesChild.iterator();
        
        assertTrue(childIterator.next() instanceof CompositeContextNodeInstance);
        
        ksession.getWorkItemManager().completeWorkItem(workItems.get(2).getId(), null);
        ksession.getWorkItemManager().completeWorkItem(workItems.get(3).getId(), null);
        
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }
}
