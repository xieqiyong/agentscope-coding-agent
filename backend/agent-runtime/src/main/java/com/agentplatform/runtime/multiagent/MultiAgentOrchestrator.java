package com.agentplatform.runtime.multiagent;

import com.agentplatform.persistence.enums.AgentRunStatus;
import com.agentplatform.runtime.agentscope.AgentScopeRuntimeAdapter;
import com.agentplatform.runtime.model.AgentRunResult;
import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventSink;
import com.agentplatform.runtime.model.RuntimeEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

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

    public AgentRunResult planOnly(RuntimeContext context, RuntimeEventSink sink) {
        MultiAgentState state = new MultiAgentState();
        state.setRuntimeContext(context);
        state.setSink(sink);
        state.setMode("PLAN_ONLY");
        state.setTask(context.getCommand().getMessage());

        emit(context, sink, RuntimeEventType.AGENT_HANDOFF, "进入多 Agent 编排",
                "Orchestrator 将任务交给 PlannerAgent", Map.of(
                        "mode", "PLAN_ONLY",
                        "from", "Orchestrator",
                        "to", plannerNode.nodeName()
                ));
        AgentNodeResult nodeResult = plannerNode.invoke(state);

        AgentRunResult result = new AgentRunResult();
        result.setRunId(context.getRunId());
        result.setConversationId(context.getConversationId());
        result.setTraceId(context.getTraceId());
        result.setAnswer(formatPlanAnswer(state.getPlan(), nodeResult));
        result.setInputTokens(estimateTokens(state.getTask()));
        result.setOutputTokens(estimateTokens(result.getAnswer()));
        result.setModelCallCount(1);
        result.setStatus(AgentRunStatus.COMPLETED.name());
        return result;
    }

    public AgentRunResult planAndExecute(RuntimeContext context, RuntimeEventSink sink) {
        MultiAgentState state = new MultiAgentState();
        state.setRuntimeContext(context);
        state.setSink(sink);
        state.setMode("PLAN_EXECUTE");
        state.setTask(context.getCommand().getMessage());
        state.setPlan(readCommandPlan(context));

        emit(context, sink, RuntimeEventType.AGENT_HANDOFF, "进入多 Agent 执行",
                "Orchestrator 将已生成计划交给 ExecutorAgent", Map.of(
                        "mode", "PLAN_EXECUTE",
                        "from", "Orchestrator",
                        "to", executorNode.nodeName()
                ));
        return executePlannedState(state);
    }

    public AgentRunResult routeAndExecute(RuntimeContext context, RuntimeEventSink sink) {
        MultiAgentState state = new MultiAgentState();
        state.setRuntimeContext(context);
        state.setSink(sink);
        state.setMode("AUTO");
        state.setTask(context.getCommand().getMessage());

        emit(context, sink, RuntimeEventType.AGENT_HANDOFF, "进入智能路由",
                "Orchestrator 将普通用户输入交给 RouterAgent 判断流程", Map.of(
                        "mode", "AUTO",
                        "from", "Orchestrator",
                        "to", routerNode.nodeName()
                ));
        AgentRouteDecision decision = routerNode.route(state);
        emitRouteSelected(context, sink, decision);

        String route = decision.effectiveRoute();
        if (AgentRouteDecision.ROUTE_PLAN_ONLY.equals(route)) {
            AgentNodeResult nodeResult = plannerNode.invoke(state);
            return buildPlanOnlyResult(context, state, nodeResult);
        }
        if (AgentRouteDecision.ROUTE_PLAN_EXECUTE.equals(route)) {
            plannerNode.invoke(state);
            AgentRunResult result = executePlannedState(state);
            result.setModelCallCount(result.getModelCallCount() + 2);
            return result;
        }
        if (AgentRouteDecision.ROUTE_DIRECT_ANSWER.equals(route)) {
            emit(context, sink, RuntimeEventType.AGENT_HANDOFF, "路由到直接回答",
                    "RouterAgent 判断本轮无需工作区工具，使用当前智能体的轻量直答模式", Map.of(
                            "mode", "AUTO",
                            "route", decision.getRoute(),
                            "effectiveRoute", route,
                            "intent", decision.getIntent()
                    ));
            AgentRunResult result = agentScopeRuntimeAdapter.executeDirectAnswer(context, sink);
            result.setModelCallCount(result.getModelCallCount() + 1);
            return result;
        }

        emit(context, sink, RuntimeEventType.AGENT_HANDOFF, "路由到单体 Agent",
                "RouterAgent 判断本轮适合交给原 ReAct Coding Agent 处理", Map.of(
                        "mode", "AUTO",
                        "route", decision.getRoute(),
                        "effectiveRoute", route,
                        "intent", decision.getIntent()
                ));
        AgentRunResult result = agentScopeRuntimeAdapter.execute(context, sink);
        result.setModelCallCount(result.getModelCallCount() + 1);
        return result;
    }

    private AgentRunResult executePlannedState(MultiAgentState state) {
        emitPlanSteps(state, "in_progress", "ExecutorAgent 开始执行计划步骤");

        AgentRunResult result = executorNode.execute(state);
        if (AgentRunStatus.WAITING_APPROVAL.name().equals(result.getStatus())) {
            return result;
        }

        String stepStatus = AgentRunStatus.COMPLETED.name().equals(result.getStatus()) ? "completed" : "failed";
        emitPlanSteps(state, stepStatus, "ExecutorAgent 已结束计划执行");
        RuntimeContext context = state.getRuntimeContext();
        emit(context, state.getSink(), RuntimeEventType.AGENT_HANDOFF, "Executor 执行结束",
                "ExecutorAgent 已返回执行结果", Map.of(
                        "mode", state.getMode(),
                        "node", executorNode.nodeName(),
                        "status", result.getStatus()
                ));
        return result;
    }

    private AgentRunResult buildPlanOnlyResult(RuntimeContext context, MultiAgentState state, AgentNodeResult nodeResult) {
        AgentRunResult result = new AgentRunResult();
        result.setRunId(context.getRunId());
        result.setConversationId(context.getConversationId());
        result.setTraceId(context.getTraceId());
        result.setAnswer(formatPlanAnswer(state.getPlan(), nodeResult));
        result.setInputTokens(estimateTokens(state.getTask()));
        result.setOutputTokens(estimateTokens(result.getAnswer()));
        result.setModelCallCount(2);
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

    private void emitPlanSteps(MultiAgentState state, String status, String content) {
        AgentPlan plan = state.getPlan();
        if (plan == null || plan.getSteps() == null) {
            return;
        }
        for (AgentPlanStep step : plan.getSteps()) {
            step.setStatus(status);
            emit(state.getRuntimeContext(), state.getSink(), RuntimeEventType.PLAN_STEP_STATUS_CHANGED,
                    "计划步骤状态变更", content, Map.of(
                            "planTitle", safe(plan.getTitle()),
                            "stepId", safe(step.getId()),
                            "stepTitle", safe(step.getTitle()),
                            "agentName", safe(step.getAgentName()),
                            "status", status,
                            "tools", step.getTools() != null ? step.getTools() : List.of()
                    ));
        }
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

    private long elapsedMs(RuntimeContext context) {
        long started = context.getRunStartedNanos();
        if (started <= 0) {
            return 0;
        }
        return (System.nanoTime() - started) / 1_000_000;
    }
}
