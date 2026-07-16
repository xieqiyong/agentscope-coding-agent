package com.agentplatform.runtime.multiagent;

import com.agentplatform.persistence.enums.AgentRunStatus;
import com.agentplatform.runtime.agentscope.AgentScopeRuntimeAdapter;
import com.agentplatform.runtime.model.AgentRunResult;
import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventSink;
import com.agentplatform.runtime.model.RuntimeEventType;
import com.agentplatform.runtime.service.AgentRunCancellationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多 Agent 编排器。
 * 中文注释：Planner 负责规划，Executor 先复用单体 Coding Agent 执行能力。
 */
@Service
public class MultiAgentOrchestrator {

    @Resource
    private PlannerNode plannerNode;

    @Resource
    private ExecutorNode executorNode;

    @Resource
    private RouterNode routerNode;

    @Resource
    private AgentScopeRuntimeAdapter agentScopeRuntimeAdapter;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private AgentRunCancellationService cancellationService;

    public AgentRunResult planOnly(RuntimeContext context, RuntimeEventSink sink) {
        MultiAgentState state = newState(context, sink, "PLAN_ONLY");
        buildPlanOnlyGraph(1).run(state);
        return state.getTerminalResult();
    }

    public AgentRunResult planAndExecute(RuntimeContext context, RuntimeEventSink sink) {
        MultiAgentState state = newState(context, sink, "PLAN_EXECUTE");
        state.setPlan(readCommandPlan(context));
        buildPlanAndExecuteGraph().run(state);
        return state.getTerminalResult();
    }

    public AgentRunResult routeAndExecute(RuntimeContext context, RuntimeEventSink sink) {
        MultiAgentState state = newState(context, sink, "AUTO");
        buildRouteGraph().run(state);
        return state.getTerminalResult();
    }

    private MultiAgentState newState(RuntimeContext context, RuntimeEventSink sink, String mode) {
        MultiAgentState state = new MultiAgentState();
        state.setRuntimeContext(context);
        state.setSink(sink);
        state.setMode(mode);
        state.setTask(context.getCommand().getMessage());
        return state;
    }

    private AgentGraph buildPlanOnlyGraph(int modelCallCount) {
        return AgentGraph.builder("handoff")
                .addNode("handoff", state -> emit(state.getRuntimeContext(), state.getSink(),
                        RuntimeEventType.AGENT_HANDOFF, "进入多 Agent 编排",
                        "Orchestrator 将任务交给 PlannerAgent", Map.of(
                                "mode", state.getMode(),
                                "from", "Orchestrator",
                                "to", plannerNode.nodeName()
                        )))
                .addEdge("handoff", "planner")
                .addNode("planner", state -> {
                    AgentNodeResult nodeResult = plannerNode.invoke(state);
                    state.setLastNodeResult(nodeResult);
                    state.setTerminalResult(buildPlanOnlyResult(state.getRuntimeContext(), state, nodeResult, modelCallCount));
                })
                .build();
    }

    private AgentGraph buildPlanAndExecuteGraph() {
        return AgentGraph.builder("handoff")
                .addNode("handoff", state -> emit(state.getRuntimeContext(), state.getSink(),
                        RuntimeEventType.AGENT_HANDOFF, "进入多 Agent 执行",
                        "Orchestrator 将已生成计划交给执行图", Map.of(
                                "mode", state.getMode(),
                                "from", "Orchestrator",
                                "to", "planExecutionGraph"
                        )))
                .addEdge("handoff", "planExecution")
                .addNode("planExecution", state -> {
                    runPlanExecutionGraph(state);
                })
                .build();
    }

    private AgentGraph buildRouteGraph() {
        return AgentGraph.builder("routeHandoff")
                .addNode("routeHandoff", state -> emit(state.getRuntimeContext(), state.getSink(),
                        RuntimeEventType.AGENT_HANDOFF, "进入智能路由",
                        "Orchestrator 将普通用户输入交给 RouterAgent 判断流程", Map.of(
                                "mode", state.getMode(),
                                "from", "Orchestrator",
                                "to", routerNode.nodeName()
                        )))
                .addEdge("routeHandoff", "router")
                .addNode("router", state -> {
                    AgentRouteDecision decision = routerNode.route(state);
                    state.setRouteDecision(decision);
                    emitRouteSelected(state.getRuntimeContext(), state.getSink(), decision);
                })
                .addConditionalEdge("router", this::selectRouteNode)
                .addNode("plannerPlanOnly", state -> {
                    AgentNodeResult nodeResult = plannerNode.invoke(state);
                    state.setLastNodeResult(nodeResult);
                    state.setTerminalResult(buildPlanOnlyResult(state.getRuntimeContext(), state, nodeResult, 2));
                })
                .addNode("plannerPlanExecute", state -> {
                    plannerNode.invoke(state);
                    AgentRunResult result = runPlanExecutionGraph(state);
                    result.setModelCallCount(result.getModelCallCount() + 2);
                    state.setTerminalResult(result);
                })
                .addNode("directAnswer", this::executeDirectAnswerNode)
                .addNode("singleAgent", this::executeSingleAgentNode)
                .build();
    }

    private AgentGraph buildPlanExecutionGraph() {
        return AgentGraph.builder("planStart")
                .addNode("planStart", state -> cancellationService.assertNotCancelled(state.getRuntimeContext().getRunId()))
                .addEdge("planStart", "stepRouter")
                .addNode("stepRouter", state -> cancellationService.assertNotCancelled(state.getRuntimeContext().getRunId()))
                .addConditionalEdge("stepRouter", this::selectPlanExecutionNode)
                .addNode("stepExecutor", this::executeCurrentPlanStep)
                .addEdge("stepExecutor", "stepRouter")
                .addNode("wholePlanExecutor", state -> state.setTerminalResult(executeWholePlan(state)))
                .addNode("finish", state -> {
                    RuntimeContext context = state.getRuntimeContext();
                    emit(context, state.getSink(), RuntimeEventType.AGENT_HANDOFF, "执行图结束",
                            "计划执行图已按边收敛到 END", Map.of(
                                    "mode", state.getMode(),
                                    "node", "finish",
                                    "status", AgentRunStatus.COMPLETED.name()
                            ));
                    state.setTerminalResult(buildAggregateResult(state, state.getStepResults(), AgentRunStatus.COMPLETED.name()));
                })
                .build();
    }

    private String selectRouteNode(MultiAgentState state) {
        AgentRouteDecision decision = state.getRouteDecision();
        String route = decision != null ? decision.effectiveRoute() : AgentRouteDecision.ROUTE_SINGLE_AGENT;
        if (AgentRouteDecision.ROUTE_PLAN_ONLY.equals(route)) {
            return "plannerPlanOnly";
        }
        if (AgentRouteDecision.ROUTE_PLAN_EXECUTE.equals(route)) {
            return "plannerPlanExecute";
        }
        if (AgentRouteDecision.ROUTE_DIRECT_ANSWER.equals(route)) {
            return "directAnswer";
        }
        return "singleAgent";
    }

    private String selectPlanExecutionNode(MultiAgentState state) {
        AgentPlan plan = state.getPlan();
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return "wholePlanExecutor";
        }
        if (state.getNextStepIndex() >= plan.getSteps().size()) {
            return "finish";
        }
        return "stepExecutor";
    }

    private AgentRunResult runPlanExecutionGraph(MultiAgentState state) {
        state.setTerminalResult(null);
        state.setNextStepIndex(0);
        state.setStepResults(new ArrayList<>());
        buildPlanExecutionGraph().run(state);
        return state.getTerminalResult();
    }

    private void executeDirectAnswerNode(MultiAgentState state) {
        RuntimeContext context = state.getRuntimeContext();
        AgentRouteDecision decision = state.getRouteDecision();
        emit(context, state.getSink(), RuntimeEventType.AGENT_HANDOFF, "路由到直接回答",
                "RouterAgent 判断本轮无需工作区工具，使用当前智能体的轻量直答模式", Map.of(
                        "mode", state.getMode(),
                        "route", decision != null ? safe(decision.getRoute()) : "",
                        "effectiveRoute", decision != null ? safe(decision.effectiveRoute()) : "",
                        "intent", decision != null ? safe(decision.getIntent()) : ""
                ));
        AgentRunResult result = agentScopeRuntimeAdapter.executeDirectAnswer(context, state.getSink());
        result.setModelCallCount(result.getModelCallCount() + 1);
        state.setTerminalResult(result);
    }

    private void executeSingleAgentNode(MultiAgentState state) {
        RuntimeContext context = state.getRuntimeContext();
        AgentRouteDecision decision = state.getRouteDecision();
        emit(context, state.getSink(), RuntimeEventType.AGENT_HANDOFF, "路由到单体 Agent",
                "RouterAgent 判断本轮适合交给原 ReAct Coding Agent 处理", Map.of(
                        "mode", state.getMode(),
                        "route", decision != null ? safe(decision.getRoute()) : "",
                        "effectiveRoute", decision != null ? safe(decision.effectiveRoute()) : "",
                        "intent", decision != null ? safe(decision.getIntent()) : ""
                ));
        AgentRunResult result = agentScopeRuntimeAdapter.execute(context, state.getSink());
        result.setModelCallCount(result.getModelCallCount() + 1);
        state.setTerminalResult(result);
    }

    private void executeCurrentPlanStep(MultiAgentState state) {
        AgentPlan plan = state.getPlan();
        if (plan == null || plan.getSteps() == null || state.getNextStepIndex() >= plan.getSteps().size()) {
            state.setTerminalResult(buildAggregateResult(state, state.getStepResults(), AgentRunStatus.COMPLETED.name()));
            return;
        }

        List<AgentPlanStep> steps = plan.getSteps();
        int stepIndex = state.getNextStepIndex();
        cancellationService.assertNotCancelled(state.getRuntimeContext().getRunId());
        AgentPlanStep step = steps.get(stepIndex);
        emitPlanStep(state, step, "in_progress", safe(step.getAgentName()) + " 开始执行计划步骤");
        emitStepAnswerHeader(state, step, stepIndex + 1, steps.size());

        AgentRunResult stepResult = executorNode.executeStep(state, step, stepIndex + 1, steps.size());
        cancellationService.assertNotCancelled(state.getRuntimeContext().getRunId());
        state.getStepResults().add(stepResult);
        if (AgentRunStatus.WAITING_APPROVAL.name().equals(stepResult.getStatus())) {
            state.setTerminalResult(stepResult);
            return;
        }

        boolean completed = AgentRunStatus.COMPLETED.name().equals(stepResult.getStatus());
        emitPlanStep(state, step, completed ? "completed" : "failed",
                completed ? safe(step.getAgentName()) + " 已完成计划步骤" : safe(step.getAgentName()) + " 执行计划步骤失败");
        state.getObservations().add(formatStepObservation(step, stepResult));
        if (!completed) {
            state.setTerminalResult(buildAggregateResult(state, state.getStepResults(), AgentRunStatus.FAILED.name()));
            return;
        }
        state.setNextStepIndex(stepIndex + 1);
    }

    private AgentRunResult executeWholePlan(MultiAgentState state) {
        AgentRunResult result = executorNode.execute(state);
        if (AgentRunStatus.WAITING_APPROVAL.name().equals(result.getStatus())) {
            return result;
        }
        RuntimeContext context = state.getRuntimeContext();
        emit(context, state.getSink(), RuntimeEventType.AGENT_HANDOFF, "Executor 执行结束",
                "ExecutorAgent 已返回执行结果", Map.of(
                        "mode", state.getMode(),
                        "node", executorNode.nodeName(),
                        "status", result.getStatus()
                ));
        return result;
    }

    private AgentRunResult buildPlanOnlyResult(RuntimeContext context, MultiAgentState state,
                                               AgentNodeResult nodeResult, int modelCallCount) {
        AgentRunResult result = new AgentRunResult();
        result.setRunId(context.getRunId());
        result.setConversationId(context.getConversationId());
        result.setTraceId(context.getTraceId());
        result.setAnswer(formatPlanAnswer(state.getPlan(), nodeResult));
        result.setInputTokens(estimateTokens(state.getTask()));
        result.setOutputTokens(estimateTokens(result.getAnswer()));
        result.setModelCallCount(modelCallCount);
        result.setStatus(AgentRunStatus.COMPLETED.name());
        return result;
    }

    private String formatPlanAnswer(AgentPlan plan, AgentNodeResult nodeResult) {
        if (plan == null) {
            return nodeResult != null ? nodeResult.getMessage() : "PlannerAgent 未能生成计划。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("已生成计划：").append(plan.getTitle()).append("\n\n");
        builder.append(plan.getSummary()).append("\n\n");
        for (AgentPlanStep step : plan.getSteps()) {
            builder.append("- [ ] ")
                    .append(step.getId())
                    .append(". ")
                    .append(step.getTitle())
                    .append("\n");
        }
        return builder.toString();
    }

    private AgentPlan readCommandPlan(RuntimeContext context) {
        Map<String, Object> plan = context.getCommand().getPlan();
        if (plan == null || plan.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.convertValue(plan, AgentPlan.class);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void emitPlanStep(MultiAgentState state, AgentPlanStep step, String status, String content) {
        if (step == null) {
            return;
        }
        step.setStatus(status);
        AgentPlan plan = state.getPlan();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("planTitle", plan != null ? safe(plan.getTitle()) : "");
        metadata.put("stepId", safe(step.getId()));
        metadata.put("stepTitle", safe(step.getTitle()));
        metadata.put("agentName", safe(step.getAgentName()));
        metadata.put("agentRole", safe(step.getAgentRole()));
        metadata.put("status", status);
        metadata.put("tools", step.getTools() != null ? step.getTools() : List.of());
        putIfNotNull(metadata, "agentId", step.getAgentId());
        putIfNotNull(metadata, "modelConfigId", step.getModelConfigId());
        putIfNotNull(metadata, "modelName", step.getModelName());
        emit(state.getRuntimeContext(), state.getSink(), RuntimeEventType.PLAN_STEP_STATUS_CHANGED,
                "计划步骤状态变更", content, metadata);
    }

    private void emitStepAnswerHeader(MultiAgentState state, AgentPlanStep step, int stepIndex, int totalSteps) {
        RuntimeContext context = state.getRuntimeContext();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stepId", safe(step.getId()));
        metadata.put("stepTitle", safe(step.getTitle()));
        metadata.put("agentName", safe(step.getAgentName()));
        metadata.put("agentRole", safe(step.getAgentRole()));
        putIfNotNull(metadata, "agentId", step.getAgentId());
        putIfNotNull(metadata, "modelConfigId", step.getModelConfigId());
        putIfNotNull(metadata, "modelName", step.getModelName());
        emit(context, state.getSink(), RuntimeEventType.ANSWER_DELTA, "计划步骤输出",
                "\n\n### 步骤 " + stepIndex + "/" + totalSteps + "：" + safe(step.getTitle()) + "\n",
                metadata);
    }

    private String formatStepObservation(AgentPlanStep step, AgentRunResult result) {
        String answer = result != null ? safe(result.getAnswer()) : "";
        String summary = answer.length() > 240 ? answer.substring(0, 240) + "..." : answer;
        return "步骤 " + safe(step.getId()) + "（" + safe(step.getTitle()) + "）结果：" + summary;
    }

    private AgentRunResult buildAggregateResult(MultiAgentState state, List<AgentRunResult> stepResults, String status) {
        RuntimeContext context = state.getRuntimeContext();
        AgentRunResult result = new AgentRunResult();
        result.setRunId(context.getRunId());
        result.setConversationId(context.getConversationId());
        result.setTraceId(context.getTraceId());
        result.setAnswer(formatAggregateAnswer(state, stepResults));
        result.setInputTokens(stepResults.stream().mapToInt(AgentRunResult::getInputTokens).sum());
        result.setOutputTokens(stepResults.stream().mapToInt(AgentRunResult::getOutputTokens).sum());
        result.setModelCallCount(stepResults.stream().mapToInt(AgentRunResult::getModelCallCount).sum());
        result.setStatus(status);
        return result;
    }

    private String formatAggregateAnswer(MultiAgentState state, List<AgentRunResult> stepResults) {
        AgentPlan plan = state.getPlan();
        StringBuilder builder = new StringBuilder();
        builder.append("计划执行结果");
        if (plan != null && plan.getTitle() != null) {
            builder.append("：").append(plan.getTitle());
        }
        builder.append("\n");
        for (int i = 0; i < stepResults.size(); i++) {
            AgentPlanStep step = plan != null && plan.getSteps() != null && i < plan.getSteps().size()
                    ? plan.getSteps().get(i)
                    : null;
            AgentRunResult stepResult = stepResults.get(i);
            builder.append("\n### 步骤 ").append(i + 1);
            if (step != null) {
                builder.append("：").append(safe(step.getTitle()));
            }
            builder.append("\n");
            builder.append(safe(stepResult.getAnswer())).append("\n");
        }
        return builder.toString().trim();
    }

    private void emitRouteSelected(RuntimeContext context, RuntimeEventSink sink, AgentRouteDecision decision) {
        emit(context, sink, RuntimeEventType.ROUTE_SELECTED, "路由已选择",
                decision.getReason(), Map.of(
                        "route", safe(decision.getRoute()),
                        "effectiveRoute", safe(decision.effectiveRoute()),
                        "intent", safe(decision.getIntent()),
                        "riskLevel", safe(decision.getRiskLevel()),
                        "confidence", decision.getConfidence(),
                        "requiresWorkspaceEvidence", decision.isRequiresWorkspaceEvidence(),
                        "requiresReview", decision.isRequiresReview()
                ));
    }

    private void emit(RuntimeContext context, RuntimeEventSink sink, RuntimeEventType type,
                      String stage, String content, Map<String, Object> metadata) {
        sink.emit(RuntimeEvent.of(
                context.getRunId(),
                context.getTraceId(),
                type,
                stage,
                content,
                metadata,
                elapsedMs(context)
        ));
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private long elapsedMs(RuntimeContext context) {
        long started = context.getRunStartedNanos();
        if (started <= 0) {
            return 0;
        }
        return (System.nanoTime() - started) / 1_000_000;
    }
}
