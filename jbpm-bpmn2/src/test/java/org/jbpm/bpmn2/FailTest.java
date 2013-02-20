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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jbpm.bpmn2.JbpmBpmn2TestCase.TestWorkItemHandler;
import org.jbpm.process.instance.impl.demo.SystemOutWorkItemHandler;
import org.jbpm.test.JbpmJUnitTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.KieBase;
import org.kie.KnowledgeBase;
import org.kie.cdi.KBase;
import org.kie.event.process.DefaultProcessEventListener;
import org.kie.event.process.ProcessStartedEvent;
import org.kie.runtime.StatefulKnowledgeSession;
import org.kie.runtime.process.ProcessInstance;
import org.kie.runtime.process.WorkItem;
import org.kie.runtime.process.WorkflowProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@RunWith(CDITestRunner.class)
public class FailTest extends JbpmJUnitTestCase {

    /**@Inject
    @KBase("fail")
    private KieBase failBase;*/

    private StatefulKnowledgeSession ksession;

    private Logger logger = LoggerFactory.getLogger(FailTest.class);

    public FailTest() {

    }

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
            ksession = null;
        }
    }

    @Test
    public void testMultipleInOutgoingSequenceFlows() throws Exception {
        System.setProperty("jbpm.enable.multi.con", "true");

        KieBase kbase = createKnowledgeBase("fail/MultipleInOutgoingSequenceFlows.bpmn2");
        ksession = createKnowledgeSession(kbase);

        final List<Long> list = new ArrayList<Long>();
        ksession.addEventListener(new DefaultProcessEventListener() {
            public void afterProcessStarted(ProcessStartedEvent event) {
                list.add(event.getProcessInstance().getId());
            }
        });

        assertEquals(0, list.size());

        ksession.fireAllRules();
        Thread.sleep(1500);

        assertEquals(1, list.size());
        System.clearProperty("jbpm.enable.multi.con");
    }

    @Test
    @Ignore
    /**
     * FIXME process build is probably caught and there is another exception instead (ArrayIndexOutOfBoundsException)
     * @throws Exception
     */
    public void testMultipleInOutgoingSequenceFlowsDisable() throws Exception {

        try {
            KieBase kbase = createKnowledgeBase("fail/MultipleInOutgoingSequenceFlows.bpmn2");
            ksession = createKnowledgeSession(kbase);

            fail("Should fail as multiple outgoing and incoming connections are disabled by default");
        }catch (Exception e) {
            assertEquals(
                    "This type of node cannot have more than one outgoing connection!",
                    e.getMessage());
        }

    }

}
