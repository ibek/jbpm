/**
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.bpmn2;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.KieBase;
import org.kie.cdi.KBase;
import org.kie.runtime.process.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(CDITestRunner.class)
public class EventTest extends JBPMTestCase {
	
    @Inject
    @KBase("event") 
    private KieBase eventBase;
    
    private Logger logger = LoggerFactory.getLogger(EventTest.class);
    
    public EventTest() {
        
    }

    @Before
    public void init() {
        createKnowledgeSession(eventBase);
    }
    
    @Test
    public void testParallelCompensate() throws Exception {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("price", 1000000);
        ProcessInstance processInstance = ksession.startProcess("ParallelCompensate", properties);
        long id = processInstance.getId();
        assertNodeTriggered(id, "Approve Order");
        assertNotNodeTriggered(id, "Process Order");
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }
    
    @Test
    public void testParallelCompensateOk() throws Exception {
        System.out.println("ok");
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("price", 100000);
        ProcessInstance processInstance = ksession.startProcess("ParallelCompensate", properties);
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
    }

}
