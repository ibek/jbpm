package org.jbpm.bpmn2;

import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

public class CDITestRunner extends BlockJUnit4ClassRunner {
    
    public CDITestRunner(Class cls) throws InitializationError {
        super(cls);
    }
    
    @Override
    protected Object createTest() throws Exception {
        WeldContainer weldContainer = new Weld().initialize();
        return weldContainer.instance().select( getTestClass().getJavaClass() ).get();
    }

}
