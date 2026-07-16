package com.agentplatform.runtime.multiagent;

import com.agentplatform.persistence.entity.AgentEntity;
import com.agentplatform.persistence.entity.ModelConfigEntity;
import com.agentplatform.runtime.agentscope.AgentScopeRuntimeAdapter;
import com.agentplatform.runtime.model.AgentRunResult;
import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventType;
import com.agentplatform.runtime.service.AgentRunContextBuilder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ExecutorAgent。
 * 中文注释：第二轮先复用原来的单体 ReAct Coding Agent 执行能力，
 * 这样文件工具、Bash、workspace 校验、SSE 事件都沿用已有稳定链路。
 */
@Component
public class ExecutorNode implements AgentNode {

    @Resource
    private AgentScopeRuntimeAdapter agentScopeRuntimeAdapter;

    @Resource
    private AgentRunContextBuilder contextBuilder;

    @Override
    public String nodeName() {
        return "ExecutorAgent";
    }

    @Override
    public AgentNodeResult invoke(MultiAgentState state) {
        AgentRunResult result = execute(state);
        return AgentNodeResult.success("ExecutorAgent 执行完成，状态：" + result.getStatus());
    }

    public AgentRunResult execute(MultiAgentState state) {
        return executeFocused(state, null, 0, 0);
    }

    public AgentRunResult executeStep(MultiAgentState state, AgentPlanStep step, int stepIndex, int totalSteps) {
        return executeFocused(state, step, stepIndex, totalSteps);
    }

    private AgentRunResult executeFocused(MultiAgentState state, AgentPlanStep step, int stepIndex, int totalSteps) {
        state.setCurrentNode(nodeName());
        RuntimeContext context = state.getRuntimeContext();
        RuntimeContextSnapshot snapshot = RuntimeContextSnapshot.capture(context);
        boolean switched = false;
        if (step != null && step.getAgentId() != null) {
            switched = contextBuilder.applyAgentOverride(context, step.getAgentId());
            if (!switched) {
                emit(state, RuntimeEventType.RUNTIME_WARNING, "Agent 选择降级",
                        "计划步骤绑定的 Agent 不可用，已回退到当前智能体执行", Map.of(
                                "node", nodeName(),
                                "stepId", safe(step.getId()),
                                "agentId", step.getAgentId()
                        ));
            }
        }
        if (step != null) {
            fillRuntimeAgentMetadata(context, step);
        }

        String stepId = step != null ? safe(step.getId()) : "";
        String stepTitle = step != null ? safe(step.getTitle()) : "";
        String activeAgentName = step != null && StringUtils.hasText(step.getAgentName()) ? step.getAgentName() : nodeName();
        Map<String, Object> handoffMetadata = new LinkedHashMap<>();
        handoffMetadata.put("node", nodeName());
        handoffMetadata.put("stepId", stepId);
        handoffMetadata.put("stepTitle", stepTitle);
        handoffMetadata.put("agentName", activeAgentName);
        handoffMetadata.put("agentRole", step != null ? safe(step.getAgentRole()) : "");
        handoffMetadata.put("stepIndex", stepIndex);
        handoffMetadata.put("totalSteps", totalSteps);
        handoffMetadata.put("switched", switched);
        if (step != null && step.getAgentId() != null) {
            handoffMetadata.put("agentId", step.getAgentId());
        }
        if (step != null && step.getModelConfigId() != null) {
            handoffMetadata.put("modelConfigId", step.getModelConfigId());
        }
        if (step != null && StringUtils.hasText(step.getModelName())) {
            handoffMetadata.put("modelName", step.getModelName());
        }
        emit(state, RuntimeEventType.AGENT_HANDOFF,
                step != null ? "切换 Agent：" + activeAgentName + " / Step " + stepId : "切换 Agent：Executor",
                step != null ? activeAgentName + " 开始执行计划步骤：" + stepTitle : "ExecutorAgent 开始按计划执行任务",
                handoffMetadata);

        String originalSystemPrompt = context.getSystemPrompt();
        context.setSystemPrompt(appendExecutorPrompt(originalSystemPrompt, state.getPlan(), step, stepIndex, totalSteps, state));
        try {
            return agentScopeRuntimeAdapter.execute(context, state.getSink());
        } finally {
            snapshot.restore(context);
        }
    }

    private void fillRuntimeAgentMetadata(RuntimeContext context, AgentPlanStep step) {
        if ((!StringUtils.hasText(step.getAgentName()) || "ExecutorAgent".equalsIgnoreCase(step.getAgentName()))
                && context.getAgent() != null && step.getAgentId() != null) {
            step.setAgentName(context.getAgent().getName());
        }
        if (step.getAgentId() == null && context.getAgent() != null) {
            step.setAgentId(context.getAgent().getId());
        }
        if (step.getModelConfigId() == null && context.getModelConfig() != null) {
            step.setModelConfigId(context.getModelConfig().getId());
        }
        if (!StringUtils.hasText(step.getModelName()) && StringUtils.hasText(context.getModelName())) {
            step.setModelName(context.getModelName());
        }
    }

    private String appendExecutorPrompt(String originalPrompt,
                                        AgentPlan plan,
                                        AgentPlanStep currentStep,
                                        int stepIndex,
                                        int totalSteps,
                                        MultiAgentState state) {
        String base = StringUtils.hasText(originalPrompt) ? originalPrompt : "";
        return base + """

                【当前多 Agent 角色】
                你现在是 ExecutorAgent。
                1. 你的任务是执行用户批准的计划，不是重新生成一份计划。
                2. 执行前仍然要读取必要文件和搜索证据，计划不能替代代码事实。
                3. 如果计划和实际代码证据冲突，以实际证据为准，并在最终回答里说明调整原因。
                4. 涉及文件修改、命令执行时，继续遵守 workspace 边界校验。
                5. 完成后用简洁清单说明实际执行了哪些动作、修改了哪些文件、还有哪些风险。
                """ + formatPlanPrompt(plan)
                + formatCurrentStepPrompt(currentStep, stepIndex, totalSteps)
                + formatPreviousObservations(state);
    }

    private String formatPlanPrompt(AgentPlan plan) {
        if (plan == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("\n【待执行计划】\n");
        builder.append("- 标题：").append(safe(plan.getTitle())).append("\n");
        builder.append("- 摘要：").append(safe(plan.getSummary())).append("\n");
        builder.append("- 风险等级：").append(safe(plan.getRiskLevel())).append("\n");
        if (plan.getSteps() != null && !plan.getSteps().isEmpty()) {
            builder.append("- 步骤：\n");
            for (AgentPlanStep step : plan.getSteps()) {
                builder.append("  ")
                        .append(safe(step.getId()))
                        .append(". ")
                        .append(safe(step.getTitle()));
                if (StringUtils.hasText(step.getDescription())) {
                    builder.append("：").append(step.getDescription());
                }
                builder.append("\n");
            }
        }
        if (plan.getAcceptanceCriteria() != null && !plan.getAcceptanceCriteria().isEmpty()) {
            builder.append("- 完成标准：\n");
            for (String item : plan.getAcceptanceCriteria()) {
                builder.append("  - ").append(safe(item)).append("\n");
            }
        }
        return builder.toString();
    }

    private String formatCurrentStepPrompt(AgentPlanStep step, int stepIndex, int totalSteps) {
        if (step == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("\n【当前只执行这一步】\n");
        builder.append("- 序号：").append(stepIndex).append("/").append(totalSteps).append("\n");
        builder.append("- 步骤 ID：").append(safe(step.getId())).append("\n");
        builder.append("- 标题：").append(safe(step.getTitle())).append("\n");
        if (StringUtils.hasText(step.getDescription())) {
            builder.append("- 说明：").append(step.getDescription()).append("\n");
        }
        if (step.getTools() != null && !step.getTools().isEmpty()) {
            builder.append("- 预期工具：").append(String.join(", ", step.getTools())).append("\n");
        }
        builder.append("要求：本轮只完成当前步骤，不要提前执行后续步骤；如果当前步骤需要后续步骤配合，在本步骤结果中说明交接信息。\n");
        return builder.toString();
    }

    private String formatPreviousObservations(MultiAgentState state) {
        if (state == null || state.getObservations() == null || state.getObservations().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("\n【前序步骤结果】\n");
        for (String observation : state.getObservations()) {
            if (StringUtils.hasText(observation)) {
                builder.append("- ").append(observation.trim()).append("\n");
            }
        }
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void emit(MultiAgentState state, RuntimeEventType type, String stage,
                      String content, Map<String, Object> metadata) {
        RuntimeContext context = state.getRuntimeContext();
        state.getSink().emit(RuntimeEvent.of(
                context.getRunId(),
                context.getTraceId(),
                type,
                stage,
                content,
                metadata,
                elapsedMs(context)
        ));
    }

    private long elapsedMs(RuntimeContext context) {
        long started = context.getRunStartedNanos();
        if (started <= 0) {
            return 0;
        }
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static class RuntimeContextSnapshot {

        private AgentEntity agent;
        private ModelConfigEntity modelConfig;
        private String systemPrompt;
        private String modelBaseUrl;
        private String modelName;
        private String apiKey;
        private int maxIterations;
        private int timeoutSeconds;
        private boolean agentScopeSessionEnabled;
        private String agentScopeSessionType;
        private String agentScopeSessionKey;
        private boolean agentScopeStateExists;

        private static RuntimeContextSnapshot capture(RuntimeContext context) {
            RuntimeContextSnapshot snapshot = new RuntimeContextSnapshot();
            snapshot.agent = context.getAgent();
            snapshot.modelConfig = context.getModelConfig();
            snapshot.systemPrompt = context.getSystemPrompt();
            snapshot.modelBaseUrl = context.getModelBaseUrl();
            snapshot.modelName = context.getModelName();
            snapshot.apiKey = context.getApiKey();
            snapshot.maxIterations = context.getMaxIterations();
            snapshot.timeoutSeconds = context.getTimeoutSeconds();
            snapshot.agentScopeSessionEnabled = context.isAgentScopeSessionEnabled();
            snapshot.agentScopeSessionType = context.getAgentScopeSessionType();
            snapshot.agentScopeSessionKey = context.getAgentScopeSessionKey();
            snapshot.agentScopeStateExists = context.isAgentScopeStateExists();
            return snapshot;
        }

        private void restore(RuntimeContext context) {
            context.setAgent(agent);
            context.setModelConfig(modelConfig);
            context.setSystemPrompt(systemPrompt);
            context.setModelBaseUrl(modelBaseUrl);
            context.setModelName(modelName);
            context.setApiKey(apiKey);
            context.setMaxIterations(maxIterations);
            context.setTimeoutSeconds(timeoutSeconds);
            context.setAgentScopeSessionEnabled(agentScopeSessionEnabled);
            context.setAgentScopeSessionType(agentScopeSessionType);
            context.setAgentScopeSessionKey(agentScopeSessionKey);
            context.setAgentScopeStateExists(agentScopeStateExists);
        }
    }
}
