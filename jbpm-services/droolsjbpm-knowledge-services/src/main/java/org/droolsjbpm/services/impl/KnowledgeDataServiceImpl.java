/*
 * Copyright 2012 JBoss by Red Hat.
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
package org.droolsjbpm.services.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.droolsjbpm.services.api.KnowledgeDataService;
import org.droolsjbpm.services.api.SessionLocator;
import org.droolsjbpm.services.impl.model.NodeInstanceDesc;
import org.droolsjbpm.services.impl.model.ProcessDesc;
import org.droolsjbpm.services.impl.model.ProcessInstanceDesc;
import org.droolsjbpm.services.impl.model.VariableStateDesc;
import org.jboss.seam.transaction.Transactional;

/**
 *
 * @author salaboy
 */
@ApplicationScoped
@Transactional
public class KnowledgeDataServiceImpl implements KnowledgeDataService {

    Map<String, SessionLocator> ksessionLocators = new HashMap<String, SessionLocator>();
    @Inject
    private EntityManager em;

    @PostConstruct
    public void init() {
    }

    public Collection<ProcessInstanceDesc> getProcessInstances() { 
        List<ProcessInstanceDesc> processInstances = em.createQuery("select pi FROM ProcessInstanceDesc pi where pi.pk = (select max(pid.pk) FROM ProcessInstanceDesc pid WHERE pid.id = pi.id ) ").getResultList();

        return processInstances;
    }
    
    public Collection<ProcessInstanceDesc> getProcessInstances(List<Integer> states, String initiator) { 
        List<ProcessInstanceDesc> processInstances = null; 
        Query query = null;
        if (initiator == null) {
            query = em.createQuery("select pi FROM ProcessInstanceDesc pi where pi.pk = (select max(pid.pk) FROM ProcessInstanceDesc pid WHERE pid.id = pi.id ) and pi.state in (:states)");
            query = query.setParameter("states", states);
        } else {
            query = em.createQuery("select pi FROM ProcessInstanceDesc pi where pi.pk = (select max(pid.pk) FROM ProcessInstanceDesc pid WHERE pid.id = pi.id and pi.initiator =:initiator) and pi.state in (:states)");
            query = query.setParameter("states", states);
            query = query.setParameter("initiator", initiator);
        }
        processInstances = query.getResultList(); 
        return processInstances;
    }

    public Collection<ProcessInstanceDesc> getProcessInstancesBySessionId(String sessionId) {
        List<ProcessInstanceDesc> processInstances = em.createQuery("select pi FROM ProcessInstanceDesc pi where pi.sessionId=:sessionId, pi.pk = (select max(pid.pk) FROM ProcessInstanceDesc pid WHERE pid.id = pi.id )")
                .setParameter("sessionId", sessionId).getResultList();

        return processInstances;
    }

    public Collection<ProcessDesc> getProcessesByDomainName(String domainName) {
        List<ProcessDesc> processes = em.createQuery("select pd from ProcessDesc pd where pd.domainName=:domainName GROUP BY pd.id ORDER BY pd.dataTimeStamp DESC")
                .setParameter("domainName", domainName).getResultList();
        return processes;
    }
    
    public Collection<ProcessDesc> getProcessesByFilter(String filter) {
        List<ProcessDesc> processes = em.createQuery("select pd from ProcessDesc pd where pd.id like :filter or pd.name like :filter ORDER BY pd.dataTimeStamp DESC")
                .setParameter("filter", filter+"%").getResultList();
        return processes;
    }

    public Collection<ProcessDesc> getProcesses() {
        List<ProcessDesc> processes = em.createQuery("select pd from ProcessDesc pd ORDER BY pd.pki DESC, pd.dataTimeStamp DESC").getResultList();
        return processes;
    }

    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessDefinition(String processDefId){
      List<ProcessInstanceDesc> processInstances = em.createQuery("select pi FROM ProcessInstanceDesc pi where pi.processDefId =:processDefId and pi.pk = (select max(pid.pk) FROM ProcessInstanceDesc pid WHERE pid.id = pi.id and pid.processDefId =:processDefId )")
                .setParameter("processDefId", processDefId).getResultList();

        return processInstances;
    }
    
    public ProcessInstanceDesc getProcessInstanceById(long processId) {
        List<ProcessInstanceDesc> processInstances = em.createQuery("select pid from ProcessInstanceDesc pid where pid.id=:processId ORDER BY pid.pk DESC")
               .setParameter("processId", processId)
               .setMaxResults(1).getResultList();

       return processInstances.get(0);
   }

    public Collection<NodeInstanceDesc> getProcessInstanceHistory(int sessionId, long processId) {
        return getProcessInstanceHistory(sessionId, processId, false);
    }

    public Collection<VariableStateDesc> getVariablesCurrentState(long processInstanceId) {
        List<VariableStateDesc> variablesState = em.createQuery("select vs FROM VariableStateDesc vs where vs.processInstanceId =:processInstanceId AND vs.pk in (select max(vss.pk) FROM VariableStateDesc vss WHERE vss.processInstanceId =:processInstanceId group by vss.variableId ) order by vs.pk")
                .setParameter("processInstanceId", processInstanceId)
                .getResultList();

        return variablesState;
    }

    public Collection<NodeInstanceDesc> getProcessInstanceHistory(int sessionId, long processId, boolean completed) {
        List<NodeInstanceDesc> nodeInstances = em.createQuery("select nid from NodeInstanceDesc nid where nid.processInstanceId=:processId AND nid.sessionId=:sessionId AND nid.completed =:completed ORDER BY nid.dataTimeStamp DESC")
                .setParameter("processId", processId)
                .setParameter("sessionId", sessionId)
                .setParameter("completed", completed)
                .getResultList();

        return nodeInstances;
    }

    public Collection<NodeInstanceDesc> getProcessInstanceFullHistory(int sessionId, long processId) {
        List<NodeInstanceDesc> nodeInstances = em.createQuery("select nid from NodeInstanceDesc nid where nid.processInstanceId=:processId AND nid.sessionId=:sessionId ORDER BY nid.dataTimeStamp DESC")
                .setParameter("processId", processId)
                .setParameter("sessionId", sessionId)
                .getResultList();

        return nodeInstances;
    }

    public Collection<NodeInstanceDesc> getProcessInstanceActiveNodes(int sessionId, long processId) {
        List<NodeInstanceDesc> completedNodeInstances = em.createQuery("select nid from NodeInstanceDesc nid where nid.processInstanceId=:processId AND nid.sessionId=:sessionId AND nid.completed =:completed ORDER BY nid.dataTimeStamp DESC")
                .setParameter("processId", processId)
                .setParameter("sessionId", sessionId)
                .setParameter("completed", true)
                .getResultList();
        
        List<NodeInstanceDesc> activeNodeInstances = em.createQuery("select nid from NodeInstanceDesc nid where nid.processInstanceId=:processId AND nid.sessionId=:sessionId AND nid.completed =:completed ORDER BY nid.dataTimeStamp DESC")
                .setParameter("processId", processId)
                .setParameter("sessionId", sessionId)
                .setParameter("completed", false)
                .getResultList();
        
        List<NodeInstanceDesc> uncompletedNodeInstances = new ArrayList<NodeInstanceDesc>(activeNodeInstances.size() - completedNodeInstances.size());
        for(NodeInstanceDesc nid : activeNodeInstances){
            boolean completed = false;
            for(NodeInstanceDesc cnid : completedNodeInstances){
                
                if(nid.getNodeId() == cnid.getNodeId()){
                    completed = true;
                }
            }
            if(!completed){
                uncompletedNodeInstances.add(nid);
            } 
        }
        

        return uncompletedNodeInstances;
    }
    
    public Collection<VariableStateDesc> getVariableHistory(long processInstanceId, String variableId) {
        List<VariableStateDesc> variablesState = em.createQuery("select vs FROM VariableStateDesc vs where vs.processInstanceId =:processInstanceId AND vs.variableId =:variableId order by vs.pk DESC")
                .setParameter("processInstanceId", processInstanceId)
                .setParameter("variableId", variableId)
                .getResultList();

        return variablesState;
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessId(
            List<Integer> states, String processId, String initiator) {
        List<ProcessInstanceDesc> processInstances = null; 
        Query query = null;
        if (initiator == null) {
            query = em.createQuery("select pi FROM ProcessInstanceDesc pi where pi.pk = (select max(pid.pk) FROM ProcessInstanceDesc pid WHERE pid.id = pi.id ) " +
            		"and pi.state in (:states) and pi.processId like :processId");
            query = query.setParameter("states", states);
            query = query.setParameter("processId", processId +"%");
        } else {
            query = em.createQuery("select pi FROM ProcessInstanceDesc pi where pi.pk = (select max(pid.pk) FROM ProcessInstanceDesc pid WHERE pid.id = pi.id  and pi.initiator =:initiator) " +
            		"and pi.state in (:states) and pi.processId like :processId");
            query = query.setParameter("states", states);
            query = query.setParameter("initiator", initiator);
            query = query.setParameter("processId", processId +"%");
        }
                
                
        processInstances = query.getResultList();
        return processInstances;

    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessName(
            List<Integer> states, String processName, String initiator) {
        List<ProcessInstanceDesc> processInstances = null; 
        Query query = null;
        if (initiator == null) {
            query = em.createQuery("select pi FROM ProcessInstanceDesc pi where pi.pk = (select max(pid.pk) FROM ProcessInstanceDesc pid WHERE pid.id = pi.id ) " +
                    "and pi.state in (:states) and pi.processName like :processName");
            query = query.setParameter("states", states);
            query = query.setParameter("processName", processName +"%");
        } else {
            query = em.createQuery("select pi FROM ProcessInstanceDesc pi where pi.pk = (select max(pid.pk) FROM ProcessInstanceDesc pid WHERE pid.id = pi.id  and pi.initiator =:initiator) " +
                    "and pi.state in (:states) and pi.processName like :processName");
            query = query.setParameter("states", states);
            query = query.setParameter("initiator", initiator);
            query = query.setParameter("processName", processName +"%");
        }
                
                
        processInstances = query.getResultList();
        return processInstances;
    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceCompletedNodes(int sessionId, long processId) {
        List<NodeInstanceDesc> completedNodeInstances = em.createQuery("select n from NodeInstanceDesc n where n.nodeId in " +
        		"(select nodeId from NodeInstanceDesc nid where nid.processInstanceId=:processId AND nid.sessionId=:sessionId AND nid.completed =:completed) ORDER BY n.nodeId, n.dataTimeStamp DESC")
                .setParameter("processId", processId)
                .setParameter("sessionId", sessionId)
                .setParameter("completed", true)
                .getResultList();
        
        return completedNodeInstances;
        
    }
}
