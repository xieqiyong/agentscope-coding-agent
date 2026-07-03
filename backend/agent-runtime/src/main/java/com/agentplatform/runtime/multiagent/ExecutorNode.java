package com.agentplatform.runtime.multiagent;

import com.agentplatform.runtime.agentscope.AgentScopeRuntimeAdapter;
import com.agentplatform.runtime.model.AgentRunResult;
import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * ExecutorAgent。
 * 中文注释：第二轮先复用原来的单体 ReAct Coding Agent 执行能力，
 * 这样文件工具、Bash、沙箱、审批、SSE 事件都沿用已有稳定链路。
 */
@Component
public class ExecutorNode implements AgentNode {

    @Resource
    private AgentScopeRuntimeAdapter agentScopeRuntimeAdapter;

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
        state.setCurrentNode(nodeName());
        RuntimeContext context = state.getRuntimeContext();
        emit(state, RuntimeEventType.AGENT_HANDOFF, "切换 Agent：Executor",
                "ExecutorAgent 开始按计划执行任务", Map.of("node", nodeName()));

        String originalSystemPrompt = context.getSystemPrompt();
        context.setSystemPrompt(appendExecutorPrompt(originalSystemPrompt, state.getPlan()));
        try {
            return agentScopeRuntimeAdapter.execute(context, state.getSink());
        } finally {
            context.setSystemPrompt(originalSystemPrompt);
        }
    }

    private String appendExecutorPrompt(String originalPrompt, AgentPlan plan) {
        String base = StringUtils.hasText(originalPrompt) ? originalPrompt : "";
        return base + """

                【当前多 Agent 角色】
                你现在是 ExecutorAgent。
                1. 你的任务是执行用户批准的计划，不是重新生成一份计划。
                2. 执行前仍然要读取必要文件和搜索证据，计划不能替代代码事实。
                3. 如果计划和实际代码证据冲突，以实际证据为准，并在最终回答里说明调整原因。
                4. 涉及文件修改、命令执行时，继续遵守平台工具权限、沙箱和审批规则。
                5. 完成后用简洁清单说明实际执行了哪些动作、修改了哪些文件、还有哪些风险。
                """ + formatPlanPrompt(plan);
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
}
