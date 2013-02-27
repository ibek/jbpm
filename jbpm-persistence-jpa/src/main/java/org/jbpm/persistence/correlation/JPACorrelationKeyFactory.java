/*
 * Copyright 2013 JBoss Inc
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
package org.jbpm.persistence.correlation;

import java.util.List;

import org.kie.process.CorrelationKey;
import org.kie.process.CorrelationKeyFactory;

public class JPACorrelationKeyFactory implements CorrelationKeyFactory {

    public CorrelationKey newCorrelationKey(String businessKey) {
        CorrelationKeyInfo correlationKey = new CorrelationKeyInfo();
        correlationKey.addProperty(new CorrelationPropertyInfo(null, businessKey));
        
        return correlationKey;
    }
    
    public CorrelationKey newCorrelationKey(List<String> properties) {
        CorrelationKeyInfo correlationKey = new CorrelationKeyInfo();
        for (String businessKey : properties) {
            correlationKey.addProperty(new CorrelationPropertyInfo(null, businessKey));
        }
        
        return correlationKey;
    }
}
