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
package org.droolsjbpm.services.impl.helpers;

import org.droolsjbpm.services.api.SessionManager;
import org.kie.command.Command;
import org.kie.event.process.ProcessEventListener;
import org.kie.event.rule.AgendaEventListener;
import org.kie.event.rule.WorkingMemoryEventListener;
import org.kie.runtime.Calendars;
import org.kie.runtime.Channel;
import org.kie.runtime.Environment;
import org.kie.runtime.Globals;
import org.kie.runtime.KieSessionConfiguration;
import org.kie.runtime.ObjectFilter;
import org.kie.runtime.StatefulKnowledgeSession;
import org.kie.runtime.process.ProcessInstance;
import org.kie.runtime.process.WorkItemManager;
import org.kie.runtime.rule.Agenda;
import org.kie.runtime.rule.AgendaFilter;
import org.kie.runtime.rule.FactHandle;
import org.kie.runtime.rule.LiveQuery;
import org.kie.runtime.rule.QueryResults;
import org.kie.runtime.rule.ViewChangedEventListener;
import org.kie.runtime.rule.SessionEntryPoint;
import org.kie.time.SessionClock;

import java.util.Collection;
import java.util.Map;
import org.kie.KieBase;
import org.kie.runtime.KieSession;

/**
 *
 * @author salaboy
 */
public class StatefulKnowledgeSessionDelegate implements KieSession{

    private KieSession ksession;
    private SessionManager sessionManager;
    private String name;

    public StatefulKnowledgeSessionDelegate(String name, KieSession ksession, SessionManager sessionManager) {
        this.name = name;
        this.ksession = ksession;
        this.sessionManager = sessionManager;
    }

    public KieSession getKsession() {
        return ksession;
    }

    public void setKsession(StatefulKnowledgeSession ksession) {
        this.ksession = ksession;
    }

    @Override
    public int getId() {
        return ksession.getId();
    }

    @Override
    public void dispose() {
        ksession.dispose();
    }

    @Override
    public <T extends SessionClock> T getSessionClock() {
        return ksession.getSessionClock();
    }

    @Override
    public void setGlobal(String string, Object o) {
        ksession.setGlobal(string, o);
    }

    @Override
    public Object getGlobal(String string) {
        return ksession.getGlobal(string);
    }

    @Override
    public Globals getGlobals() {
        return ksession.getGlobals();
    }

    @Override
    public Calendars getCalendars() {
        return ksession.getCalendars();
    }

    @Override
    public Environment getEnvironment() {
        return ksession.getEnvironment();
    }

    @Override
    public KieBase getKieBase() {
        return ksession.getKieBase();
    }

    @Override
    public void registerChannel(String string, Channel chnl) {
        ksession.registerChannel(string, chnl);
    }

    @Override
    public void unregisterChannel(String string) {
        ksession.unregisterChannel(string);
    }

    @Override
    public Map<String, Channel> getChannels() {
        return ksession.getChannels();
    }

    @Override
    public KieSessionConfiguration getSessionConfiguration() {
        return ksession.getSessionConfiguration();
    }

    @Override
    public void halt() {
        ksession.halt();
    }

    @Override
    public Agenda getAgenda() {
        return ksession.getAgenda();
    }

    @Override
    public SessionEntryPoint getEntryPoint(String string) {
        return ksession.getEntryPoint(string);
    }

    @Override
    public Collection<? extends SessionEntryPoint> getEntryPoints() {
        return ksession.getEntryPoints();
    }

    @Override
    public QueryResults getQueryResults(String string, Object... os) {
        return ksession.getQueryResults(string, os);
    }

    @Override
    public LiveQuery openLiveQuery(String string, Object[] os, ViewChangedEventListener vl) {
        return ksession.openLiveQuery(string, os, vl);
    }

    @Override
    public void addEventListener(ProcessEventListener pl) {
        ksession.addEventListener(pl);
    }

    @Override
    public void removeEventListener(ProcessEventListener pl) {
        ksession.removeEventListener(pl);
    }

    @Override
    public Collection<ProcessEventListener> getProcessEventListeners() {
        return ksession.getProcessEventListeners();
    }

    @Override
    public void addEventListener(WorkingMemoryEventListener wl) {
        ksession.addEventListener(wl);
    }

    @Override
    public void removeEventListener(WorkingMemoryEventListener wl) {
        ksession.removeEventListener(wl);
    }

    @Override
    public Collection<WorkingMemoryEventListener> getWorkingMemoryEventListeners() {
        return ksession.getWorkingMemoryEventListeners();
    }

    @Override
    public void addEventListener(AgendaEventListener al) {
        ksession.addEventListener(al);
    }

    @Override
    public void removeEventListener(AgendaEventListener al) {
        ksession.removeEventListener(al);
    }

    @Override
    public Collection<AgendaEventListener> getAgendaEventListeners() {
        return ksession.getAgendaEventListeners();
    }

    @Override
    public <T> T execute(Command<T> cmnd) {
        return ksession.execute(cmnd);
    }

    @Override
    public ProcessInstance startProcess(String string) {
        ProcessInstance processInstance = ksession.createProcessInstance(string, null);
        sessionManager.addProcessInstanceIdKsession(ksession.getId(), processInstance.getId());
        processInstance = ksession.startProcessInstance(processInstance.getId());
        return processInstance;
    }

    @Override
    public ProcessInstance startProcess(String string, Map<String, Object> map) {
        ProcessInstance processInstance = ksession.createProcessInstance(string, map);
        sessionManager.addProcessInstanceIdKsession(ksession.getId(), processInstance.getId() );
        processInstance = ksession.startProcessInstance(processInstance.getId());
        return processInstance;
    }

    @Override
    public ProcessInstance createProcessInstance(String string, Map<String, Object> map) {
        ProcessInstance processInstance = ksession.createProcessInstance(string, map);
        sessionManager.addProcessInstanceIdKsession(ksession.getId(), processInstance.getId());
        return processInstance;
    }

    @Override
    public ProcessInstance startProcessInstance(long l) {
        ProcessInstance processInstance = ksession.startProcessInstance(l);
        sessionManager.addProcessInstanceIdKsession(ksession.getId(), processInstance.getId());
        return processInstance;
    }

    @Override
    public void signalEvent(String string, Object o) {
        ksession.signalEvent(string, o);
    }

    @Override
    public void signalEvent(String string, Object o, long l) {
        ksession.signalEvent(string, o, l);
    }

    @Override
    public Collection<ProcessInstance> getProcessInstances() {
        return ksession.getProcessInstances();
    }

    @Override
    public ProcessInstance getProcessInstance(long l) {
        return ksession.getProcessInstance(l);
    }

    @Override
    public ProcessInstance getProcessInstance(long l, boolean b) {
        return ksession.getProcessInstance(l, b);
    }

    @Override
    public void abortProcessInstance(long l) {
        ksession.abortProcessInstance(l);
    }

    @Override
    public WorkItemManager getWorkItemManager() {
        return ksession.getWorkItemManager();
    }

    @Override
    public String getEntryPointId() {
        return ksession.getEntryPointId();
    }

    @Override
    public FactHandle insert(Object o) {
        return ksession.insert(o);
    }

    @Override
    public void retract(FactHandle fh) {
        ksession.retract(fh);
    }

    @Override
    public void delete(FactHandle fh) {
        ksession.delete(fh);
    }

    @Override
    public void update(FactHandle fh, Object o) {
        ksession.update(fh, o);
    }

    @Override
    public FactHandle getFactHandle(Object o) {
        return ksession.getFactHandle(o);
    }

    @Override
    public Object getObject(FactHandle fh) {
        return ksession.getObject(fh);
    }

    @Override
    public Collection<Object> getObjects() {
        return ksession.getObjects();
    }

    @Override
    public Collection<Object> getObjects(ObjectFilter of) {
        return ksession.getObjects(of);
    }

    @Override
    public <T extends FactHandle> Collection<T> getFactHandles() {
        return ksession.getFactHandles();
    }

    @Override
    public <T extends FactHandle> Collection<T> getFactHandles(ObjectFilter of) {
        return ksession.getFactHandles(of);
    }

    @Override
    public long getFactCount() {
        return ksession.getFactCount();
    }

    @Override
    public int fireAllRules() {
        return ksession.fireAllRules();
    }

    @Override
    public int fireAllRules(int i) {
        return ksession.fireAllRules(i);
    }

    @Override
    public int fireAllRules(AgendaFilter af) {
        return ksession.fireAllRules(af);
    }

    @Override
    public int fireAllRules(AgendaFilter af, int i) {
        return ksession.fireAllRules(af, i);
    }

    @Override
    public void fireUntilHalt() {
        ksession.fireUntilHalt();
    }

    @Override
    public void fireUntilHalt(AgendaFilter af) {
        ksession.fireUntilHalt(af);
    }

  
}
