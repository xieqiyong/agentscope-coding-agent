package com.agentplatform.runtime.multiagent;

import com.agentplatform.persistence.entity.AgentPlanStateEntity;
import com.agentplatform.persistence.enums.AgentRunStatus;
import com.agentplatform.persistence.repository.AgentPlanStateRepository;
import com.agentplatform.runtime.agentscope.AgentScopeRuntimeAdapter;
import com.agentplatform.runtime.multiagent.infrastructure.MultiAgentExecutionProperties;
import com.agentplatform.runtime.multiagent.infrastructure.MultiAgentTaskExecutor;
import com.agentplatform.runtime.model.AgentRunResult;
import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventSink;
import com.agentplatform.runtime.model.RuntimeEventType;
import com.agentplatform.runtime.service.AgentRunCancellationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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

    @Resource
    private AgentPlanStateRepository agentPlanStateRepository;

    @Resource
    private MultiAgentTaskExecutor multiAgentTaskExecutor;

    @Resource
    private MultiAgentExecutionProperties multiAgentExecutionProperties;

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
                .addNode("planExecution", state -> state.setTerminalResult(runPlanExecutionGraph(state)))
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

    private AgentRunResult runPlanExecutionGraph(MultiAgentState state) {
        state.setTerminalResult(null);
        state.setStepResults(new ArrayList<>());
        state.setNodeResults(new LinkedHashMap<>());
        if (state.getPlan() == null || state.getPlan().getSteps() == null || state.getPlan().getSteps().isEmpty()) {
            return executeWholePlan(state);
        }

        resetRetryableNodeStatuses(state.getPlan());
        TaskGraph taskGraph = TaskGraph.from(state.getPlan());
        if (taskGraph.allCompleted()) {
            return buildAggregateResult(state, AgentRunStatus.COMPLETED.name());
        }

        // 创建图执行进度记录，用于中断后按节点状态续接。
        AgentPlanStateEntity planState = createPlanState(state);
        state.setPlanStateId(planState.getId());
        return executeTaskGraph(state, taskGraph);
    }

    private AgentRunResult executeTaskGraph(MultiAgentState state, TaskGraph taskGraph) {
        RuntimeContext context = state.getRuntimeContext();
        int maxConcurrency = resolveMaxConcurrency(state.getPlan());
        emit(context, state.getSink(), RuntimeEventType.TASK_GRAPH_STARTED, "任务图开始执行",
                "调度器开始按依赖关系执行任务节点", Map.of(
                        "graphVersion", state.getPlan().getGraphVersion(),
                        "maxConcurrency", maxConcurrency,
                        "nodeCount", taskGraph.nodes().size()
                ));

        MultiAgentScheduler scheduler = new MultiAgentScheduler(multiAgentTaskExecutor.executor(), maxConcurrency);
        try {
            MultiAgentScheduler.ScheduleResult scheduleResult = scheduler.run(
                    taskGraph,
                    node -> executeTaskNode(state, node),
                    scheduleListener(state, taskGraph),
                    () -> cancellationService.assertNotCancelled(context.getRunId())
            );
            if (scheduleResult.waiting()) {
                persistPlanState(state, "WAITING_APPROVAL");
                return scheduleResult.results().values().stream()
                        .filter(result -> AgentRunStatus.WAITING_APPROVAL.name().equals(result.getStatus()))
                        .findFirst()
                        .orElseGet(() -> buildAggregateResult(state, AgentRunStatus.WAITING_APPROVAL.name()));
            }
            if (!scheduleResult.completed()) {
                persistPlanState(state, "FAILED");
                throw new IllegalStateException("任务图执行失败，未完成节点：" + scheduleResult.unfinishedNodeIds());
            }
            persistPlanState(state, "COMPLETED");
            AgentRunResult result = buildAggregateResult(state, AgentRunStatus.COMPLETED.name());
            emitAggregateAnswer(state, result.getAnswer());
            return result;
        } catch (Exception e) {
            if (cancellationService.isCancelled(state.getRuntimeContext().getRunId())) {
                markActiveNodesCancelled(state);
                persistPlanState(state, "INTERRUPTED");
            } else {
                persistPlanState(state, "FAILED");
            }
            throw e;
        }
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

    private AgentRunResult executeTaskNode(MultiAgentState state, AgentPlanStep node) {
        Long runId = state.getRuntimeContext().getRunId();
        cancellationService.bindCurrentThread(runId);
        try {
            int nodeIndex = state.getPlan().getSteps().indexOf(node) + 1;
            return executorNode.executeStep(state, node, nodeIndex, state.getPlan().getSteps().size());
        } finally {
            cancellationService.unbindCurrentThread(runId);
        }
    }

    private MultiAgentScheduler.ScheduleListener scheduleListener(MultiAgentState state, TaskGraph taskGraph) {
        return new MultiAgentScheduler.ScheduleListener() {
            @Override
            public void onNodeStarted(AgentPlanStep node) {
                node.setAttempt(node.getAttempt() + 1);
                node.setStartedAt(LocalDateTime.now().toString());
                node.setFinishedAt(null);
                node.setErrorMessage(null);
                emitPlanStep(state, node, TaskNodeStatus.RUNNING.value(),
                        safe(node.getAgentName()) + " 开始执行任务节点");
                persistPlanState(state, "RUNNING");
            }

            @Override
            public void onNodeCompleted(AgentPlanStep node, AgentRunResult result) {
                node.setOutput(result != null ? safe(result.getAnswer()) : "");
                node.setFinishedAt(LocalDateTime.now().toString());
                node.setErrorMessage(null);
                state.getNodeResults().put(node.getId(), result);
                state.getObservations().add(formatStepObservation(node, result));
                emitPlanStep(state, node, TaskNodeStatus.COMPLETED.value(),
                        safe(node.getAgentName()) + " 已完成任务节点");
                persistPlanState(state, "RUNNING");
            }

            @Override
            public void onNodeWaiting(AgentPlanStep node, AgentRunResult result) {
                node.setOutput(result != null ? safe(result.getAnswer()) : "");
                node.setFinishedAt(LocalDateTime.now().toString());
                state.getNodeResults().put(node.getId(), result);
                emitPlanStep(state, node, TaskNodeStatus.WAITING.value(),
                        safe(node.getAgentName()) + " 正在等待继续执行");
                persistPlanState(state, "WAITING_APPROVAL");
            }

            @Override
            public void onNodeFailed(AgentPlanStep node, Throwable error) {
                node.setFinishedAt(LocalDateTime.now().toString());
                node.setErrorMessage(error != null ? safe(error.getMessage()) : "节点执行失败");
                emitPlanStep(state, node, TaskNodeStatus.FAILED.value(),
                        safe(node.getAgentName()) + " 执行任务节点失败");
                persistPlanState(state, "FAILED");
            }

            @Override
            public void onGraphFinished(TaskGraph graph, MultiAgentScheduler.ScheduleResult result) {
                String status = result.completed() ? "COMPLETED" : result.waiting() ? "WAITING_APPROVAL" : "FAILED";
                emit(state.getRuntimeContext(), state.getSink(), RuntimeEventType.TASK_GRAPH_FINISHED,
                        "任务图执行结束", "任务图已完成本轮调度", Map.of(
                                "status", status,
                                "completedNodes", taskGraph.completedCount(),
                                "totalNodes", taskGraph.nodes().size(),
                                "unfinishedNodeIds", result.unfinishedNodeIds()
                        ));
            }
        };
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
        metadata.put("dependsOn", step.getDependsOn() != null ? step.getDependsOn() : List.of());
        metadata.put("attempt", step.getAttempt());
        metadata.put("output", safe(step.getOutput()));
        metadata.put("errorMessage", safe(step.getErrorMessage()));
        metadata.put("startedAt", safe(step.getStartedAt()));
        metadata.put("finishedAt", safe(step.getFinishedAt()));
        putIfNotNull(metadata, "agentId", step.getAgentId());
        putIfNotNull(metadata, "modelConfigId", step.getModelConfigId());
        putIfNotNull(metadata, "modelName", step.getModelName());
        emit(state.getRuntimeContext(), state.getSink(), RuntimeEventType.PLAN_STEP_STATUS_CHANGED,
                "计划步骤状态变更", content, metadata);
    }

    private String formatStepObservation(AgentPlanStep step, AgentRunResult result) {
        String answer = result != null ? safe(result.getAnswer()) : "";
        String summary = answer.length() > 240 ? answer.substring(0, 240) + "..." : answer;
        return "步骤 " + safe(step.getId()) + "（" + safe(step.getTitle()) + "）结果：" + summary;
    }

    private int resolveMaxConcurrency(AgentPlan plan) {
        int globalLimit = Math.max(1, multiAgentExecutionProperties.getMaxConcurrency());
        int planLimit = plan != null && plan.getMaxConcurrency() > 0 ? plan.getMaxConcurrency() : globalLimit;
        return Math.max(1, Math.min(globalLimit, planLimit));
    }

    private void resetRetryableNodeStatuses(AgentPlan plan) {
        if (plan == null || plan.getSteps() == null) {
            return;
        }
        for (AgentPlanStep step : plan.getSteps()) {
            TaskNodeStatus status = TaskNodeStatus.from(step.getStatus());
            if (status != TaskNodeStatus.COMPLETED && status != TaskNodeStatus.PENDING) {
                step.setStatus(TaskNodeStatus.PENDING.value());
                step.setStartedAt(null);
                step.setFinishedAt(null);
                step.setErrorMessage(null);
            }
        }
    }

    private void markActiveNodesCancelled(MultiAgentState state) {
        if (state.getPlan() == null || state.getPlan().getSteps() == null) {
            return;
        }
        for (AgentPlanStep step : state.getPlan().getSteps()) {
            TaskNodeStatus status = TaskNodeStatus.from(step.getStatus());
            if (status == TaskNodeStatus.RUNNING || status == TaskNodeStatus.READY) {
                step.setFinishedAt(LocalDateTime.now().toString());
                emitPlanStep(state, step, TaskNodeStatus.CANCELLED.value(),
                        safe(step.getAgentName()) + " 的任务节点已中断");
            }
        }
    }

    private void emitAggregateAnswer(MultiAgentState state, String answer) {
        if (!StringUtils.hasText(answer)) {
            return;
        }
        Map<String, Object> metadata = Map.of("source", "MULTI_AGENT_AGGREGATE");
        emit(state.getRuntimeContext(), state.getSink(), RuntimeEventType.ANSWER_STARTED,
                "汇总多 Agent 结果", null, metadata);
        emit(state.getRuntimeContext(), state.getSink(), RuntimeEventType.ANSWER_DELTA,
                "多 Agent 最终结果", answer, metadata);
        emit(state.getRuntimeContext(), state.getSink(), RuntimeEventType.ANSWER_FINISHED,
                "多 Agent 结果汇总完成", null, metadata);
    }

    private AgentRunResult buildAggregateResult(MultiAgentState state, String status) {
        RuntimeContext context = state.getRuntimeContext();
        List<AgentRunResult> currentRunResults = new ArrayList<>(state.getNodeResults().values());
        AgentRunResult result = new AgentRunResult();
        result.setRunId(context.getRunId());
        result.setConversationId(context.getConversationId());
        result.setTraceId(context.getTraceId());
        result.setAnswer(formatAggregateAnswer(state));
        result.setInputTokens(currentRunResults.stream().mapToInt(AgentRunResult::getInputTokens).sum());
        result.setOutputTokens(currentRunResults.stream().mapToInt(AgentRunResult::getOutputTokens).sum());
        // 缓存命中与成本同样按节点结果累加，保证多 Agent 汇总口径与单 Agent 一致
        result.setCachedTokens(currentRunResults.stream().mapToInt(AgentRunResult::getCachedTokens).sum());
        result.setCostUsd(currentRunResults.stream().mapToDouble(AgentRunResult::getCostUsd).sum());
        result.setModelCallCount(currentRunResults.stream().mapToInt(AgentRunResult::getModelCallCount).sum());
        result.setStatus(status);
        return result;
    }

    private String formatAggregateAnswer(MultiAgentState state) {
        AgentPlan plan = state.getPlan();
        StringBuilder builder = new StringBuilder();
        builder.append("多智能体协作结果");
        if (plan != null && plan.getTitle() != null) {
            builder.append("：").append(plan.getTitle());
        }
        builder.append("\n");
        if (plan == null || plan.getSteps() == null) {
            return builder.toString().trim();
        }
        for (int i = 0; i < plan.getSteps().size(); i++) {
            AgentPlanStep step = plan.getSteps().get(i);
            builder.append("\n### ").append(i + 1).append(". ").append(safe(step.getTitle()));
            if (StringUtils.hasText(step.getAgentName())) {
                builder.append("（").append(step.getAgentName()).append("）");
            }
            builder.append("\n");
            if (StringUtils.hasText(step.getOutput())) {
                builder.append(step.getOutput()).append("\n");
            } else if (StringUtils.hasText(step.getErrorMessage())) {
                builder.append("执行失败：").append(step.getErrorMessage()).append("\n");
            } else {
                builder.append("该节点没有持久化输出。\n");
            }
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

    // ===== plan 执行进度持久化（中断续接） =====

    private AgentPlanStateEntity createPlanState(MultiAgentState state) {
        AgentPlanStateEntity entity = new AgentPlanStateEntity();
        entity.setConversationId(state.getRuntimeContext().getConversationId());
        entity.setRunId(state.getRuntimeContext().getRunId());
        try {
            entity.setPlanJson(objectMapper.writeValueAsString(state.getPlan()));
        } catch (Exception e) {
            entity.setPlanJson("{}");
        }
        entity.setNextStepIndex(completedNodeCount(state.getPlan()));
        entity.setStatus("RUNNING");
        return agentPlanStateRepository.save(entity);
    }

    private void persistPlanState(MultiAgentState state, String status) {
        if (state.getPlanStateId() == null) {
            return;
        }
        agentPlanStateRepository.findById(state.getPlanStateId()).ifPresent(entity -> {
            try {
                entity.setPlanJson(objectMapper.writeValueAsString(state.getPlan()));
            } catch (Exception ignored) {
                // 中文注释：保留上一次可恢复快照，序列化失败不覆盖已有 planJson。
            }
            entity.setNextStepIndex(completedNodeCount(state.getPlan()));
            entity.setStatus(status);
            agentPlanStateRepository.save(entity);
        });
    }

    private int completedNodeCount(AgentPlan plan) {
        if (plan == null || plan.getSteps() == null) {
            return 0;
        }
        return (int) plan.getSteps().stream()
                .filter(step -> TaskNodeStatus.from(step.getStatus()) == TaskNodeStatus.COMPLETED)
                .count();
    }

    /**
     * 从中断点续接 plan 执行。
     * 还原 plan，把已完成步骤（< nextStepIndex）标记 completed、其余 pending，从 nextStepIndex 继续执行剩余步骤。
     */
    public AgentRunResult resumePlanAndExecute(RuntimeContext context, RuntimeEventSink sink,
                                               AgentPlanStateEntity planState) {
        MultiAgentState state = newState(context, sink, "PLAN_EXECUTE");
        AgentPlan plan;
        try {
            plan = objectMapper.readValue(planState.getPlanJson(), AgentPlan.class);
        } catch (Exception e) {
            throw new RuntimeException("恢复计划失败，planJson 解析异常：" + e.getMessage(), e);
        }
        int resumeIndex = planState.getNextStepIndex();
        restoreNodeStatusesForResume(plan, resumeIndex);
        state.setPlan(plan);
        state.setStepResults(new ArrayList<>());
        state.setNodeResults(new LinkedHashMap<>());

        // 原图状态记录继续作为本次运行的 checkpoint。
        planState.setStatus("RUNNING");
        AgentPlanStateEntity running = agentPlanStateRepository.save(planState);
        state.setPlanStateId(running.getId());

        emit(context, sink, RuntimeEventType.RUNTIME_WARNING, "计划续接",
                "检测到未完成的任务图，将从所有依赖已满足的节点继续执行",
                Map.of("mode", state.getMode(), "completedNodes", completedNodeCount(plan)));

        TaskGraph taskGraph = TaskGraph.from(plan);
        if (taskGraph.allCompleted()) {
            persistPlanState(state, "COMPLETED");
            AgentRunResult result = buildAggregateResult(state, AgentRunStatus.COMPLETED.name());
            emitAggregateAnswer(state, result.getAnswer());
            return result;
        }
        return executeTaskGraph(state, taskGraph);
    }

    private void restoreNodeStatusesForResume(AgentPlan plan, int legacyResumeIndex) {
        if (plan == null || plan.getSteps() == null) {
            return;
        }
        boolean legacyPlan = plan.getGraphVersion() < 2;
        for (int i = 0; i < plan.getSteps().size(); i++) {
            AgentPlanStep step = plan.getSteps().get(i);
            if (legacyPlan && i < legacyResumeIndex) {
                step.setStatus(TaskNodeStatus.COMPLETED.value());
                continue;
            }
            if (TaskNodeStatus.from(step.getStatus()) != TaskNodeStatus.COMPLETED) {
                step.setStatus(TaskNodeStatus.PENDING.value());
                step.setStartedAt(null);
                step.setFinishedAt(null);
                step.setErrorMessage(null);
            }
        }
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
