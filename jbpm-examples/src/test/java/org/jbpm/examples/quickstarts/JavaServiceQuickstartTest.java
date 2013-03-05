package org.jbpm.examples.quickstarts;

import java.util.HashMap;
import java.util.Map;

import org.jbpm.test.JBPMJUnitTestCase;
import org.junit.Test;
import org.kie.runtime.StatefulKnowledgeSession;

/**
 * This is a sample file to test a process.
 */
public class JavaServiceQuickstartTest extends JBPMJUnitTestCase {

	@Test
	public void testProcess() throws Exception {
		StatefulKnowledgeSession ksession = createKnowledgeSession("quickstarts/ScriptTask.bpmn");
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("person", new Person("krisv"));
		ksession.startProcess("com.sample.script", params);
	}

}