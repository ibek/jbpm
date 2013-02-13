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

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.KieBase;
import org.kie.cdi.KBase;
import org.kie.event.process.DefaultProcessEventListener;
import org.kie.event.process.ProcessStartedEvent;
import org.kie.event.process.ProcessVariableChangedEvent;
import org.kie.runtime.process.ProcessInstance;
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

}
