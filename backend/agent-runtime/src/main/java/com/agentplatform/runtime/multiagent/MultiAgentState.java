package com.agentplatform.runtime.multiagent;

import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEventSink;

import java.util.ArrayList;
import java.util.List;

/**
 * 多 Agent 共享状态。
 * 中文注释：Agent 节点之间不互相塞聊天记录，而是读写这个结构化状态。
 */
public class MultiAgentState {

    private RuntimeContext runtimeContext;
    private RuntimeEventSink sink;
    private String mode;
    private String task;
    private AgentPlan plan;
    private String currentNode;
    private List<String> observations = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public RuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public void setRuntimeContext(RuntimeContext runtimeContext) {
        this.runtimeContext = runtimeContext;
    }

    public RuntimeEventSink getSink() {
        return sink;
    }

    public void setSink(RuntimeEventSink sink) {
        this.sink = sink;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public AgentPlan getPlan() {
        return plan;
    }

    public void setPlan(AgentPlan plan) {
        this.plan = plan;
    }

    public String getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(String currentNode) {
        this.currentNode = currentNode;
    }

    public List<String> getObservations() {
        return observations;
    }

    public void setObservations(List<String> observations) {
        this.observations = observations;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
