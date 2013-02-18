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

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.KieBase;
import org.kie.KnowledgeBase;
import org.kie.KnowledgeBaseFactory;
import org.kie.builder.KnowledgeBuilder;
import org.kie.builder.KnowledgeBuilderError;
import org.kie.builder.KnowledgeBuilderFactory;
import org.kie.cdi.KBase;
import org.kie.event.process.DefaultProcessEventListener;
import org.kie.event.process.ProcessStartedEvent;
import org.kie.event.process.ProcessVariableChangedEvent;
import org.kie.io.ResourceFactory;
import org.kie.io.ResourceType;
import org.kie.runtime.StatefulKnowledgeSession;
import org.kie.runtime.process.ProcessInstance;
import org.kie.runtime.process.WorkflowProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(CDITestRunner.class)
public class ActivityTest extends JBPMTestCase {

    @Inject
    @KBase("activity")
    private KieBase activityBase;

    private Logger logger = LoggerFactory.getLogger(ActivityTest.class);

    public ActivityTest() {

    }

    @Before
    public void init() {
        createKnowledgeSession(activityBase);
    }
    
    @Test
    public void testMinimalProcess() throws Exception {
        ProcessInstance processInstance = ksession.startProcess("Minimal");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMinimalProcessImplicit() throws Exception {
        ProcessInstance processInstance = ksession.startProcess("MinimalImplicit");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMinimalProcessWithGraphical() throws Exception {
        ProcessInstance processInstance = ksession.startProcess("MinimalWithGraphical");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testMinimalProcessWithDIGraphical() throws Exception {
        ProcessInstance processInstance = ksession.startProcess("MinimalWithDIGraphical");
        assertTrue(processInstance.getState() == ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testCompositeProcessWithDIGraphical() throws Exception {
        ProcessInstance processInstance = ksession.startProcess("CompositeWithDIGraphical");
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
    public void testCallActivity() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "oldValue");
        ProcessInstance processInstance = ksession.startProcess(
                "CallActivity", params);
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

}
