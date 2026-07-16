package com.agentplatform.runtime.multiagent;

import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEventSink;
import com.agentplatform.runtime.model.AgentRunResult;

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
    private String graphNode;
    private AgentRouteDecision routeDecision;
    private AgentNodeResult lastNodeResult;
    private AgentRunResult terminalResult;
    private List<AgentRunResult> stepResults = new ArrayList<>();
    private int nextStepIndex;
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

    public String getGraphNode() {
        return graphNode;
    }

    public void setGraphNode(String graphNode) {
        this.graphNode = graphNode;
    }

    public AgentRouteDecision getRouteDecision() {
        return routeDecision;
    }

    public void setRouteDecision(AgentRouteDecision routeDecision) {
        this.routeDecision = routeDecision;
    }

    public AgentNodeResult getLastNodeResult() {
        return lastNodeResult;
    }

    public void setLastNodeResult(AgentNodeResult lastNodeResult) {
        this.lastNodeResult = lastNodeResult;
    }

    public AgentRunResult getTerminalResult() {
        return terminalResult;
    }

    public void setTerminalResult(AgentRunResult terminalResult) {
        this.terminalResult = terminalResult;
    }

    public List<AgentRunResult> getStepResults() {
        return stepResults;
    }

    public void setStepResults(List<AgentRunResult> stepResults) {
        this.stepResults = stepResults;
    }

    public int getNextStepIndex() {
        return nextStepIndex;
    }

    public void setNextStepIndex(int nextStepIndex) {
        this.nextStepIndex = nextStepIndex;
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
