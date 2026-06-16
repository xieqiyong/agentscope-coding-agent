package com.agentplatform.runtime.multiagent;

import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PlannerAgent。
 * 中文注释：第一轮 Planner 只生成结构化计划，不注册写入工具，也不执行 Bash。
 */
@Component
public class PlannerNode implements AgentNode {

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public String nodeName() {
        return "PlannerAgent";
    }

    @Override
    public AgentNodeResult invoke(MultiAgentState state) {
        state.setCurrentNode(nodeName());
        RuntimeContext context = state.getRuntimeContext();
        emit(state, RuntimeEventType.AGENT_HANDOFF, "切换 Agent：Planner",
                "PlannerAgent 开始生成结构化计划", Map.of("node", nodeName()));

        String rawPlan = "";
        try (ReActAgent agent = buildPlannerAgent(context)) {
            PlannerTrace trace = new PlannerTrace();
            agent.streamEvents(List.of(new UserMessage(buildPlannerInput(state))))
                    .doOnNext(event -> recordPlannerEvent(state, trace, event))
                    .collectList()
                    .block(Duration.ofSeconds(plannerTimeoutSeconds(context)));
            rawPlan = trace.answer().trim();
        } catch (Exception e) {
            state.getErrors().add("PlannerAgent 调用失败：" + e.getMessage());
            emit(state, RuntimeEventType.RUNTIME_WARNING, "Planner 降级",
                    "PlannerAgent 调用失败，已使用规则计划兜底：" + e.getMessage(), Map.of("node", nodeName()));
        }

        AgentPlan plan = parsePlan(rawPlan);
        if (plan == null) {
            plan = fallbackPlan(state.getTask());
        }
        normalizePlan(plan, state.getTask());
        state.setPlan(plan);
        emitPlanCreated(state, plan);
        return AgentNodeResult.success("PlannerAgent 已生成计划：" + plan.getTitle());
    }

    private ReActAgent buildPlannerAgent(RuntimeContext context) {
        return ReActAgent.builder()
                .name("planner-agent")
                .description("只负责规划、不修改文件的计划智能体")
                .sysPrompt(buildPlannerPrompt(context))
                .model(buildModel(context))
                .toolkit(new Toolkit())
                .maxIters(1)
                .build();
    }

    private OpenAIChatModel buildModel(RuntimeContext context) {
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .baseUrl(normalizeModelBaseUrl(context.getModelBaseUrl()))
                .modelName(context.getModelName())
                .stream(true);
        if (StringUtils.hasText(context.getApiKey())) {
            builder.apiKey(context.getApiKey());
        }
        return builder.build();
    }

    private String buildPlannerPrompt(RuntimeContext context) {
        return """
                你是 PlannerAgent，只负责把用户任务拆成可执行计划。

                约束：
                1. 不要修改文件。
                2. 不要执行 Bash。
                3. 不要声称已经读取了代码；如果计划依赖代码事实，只能把读取动作放入步骤。
                4. 输出必须是严格 JSON，不要 Markdown，不要代码块，不要解释。
                5. steps 中的 status 固定为 pending。
                6. tools 只能从 LS、Read、Grep、Glob、WebSearch、Edit、Write、apply_patch、Bash 中选择；计划阶段只是声明可能需要的工具。

                JSON Schema：
                {
                  "title": "计划标题",
                  "summary": "一句话说明",
                  "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                  "steps": [
                    {
                      "id": "1",
                      "title": "步骤标题",
                      "description": "步骤说明",
                      "status": "pending",
                      "agentName": "ExecutorAgent",
                      "tools": ["Read", "Grep"]
                    }
                  ],
                  "acceptanceCriteria": ["完成标准"],
                  "expectedTools": ["Read", "Grep"],
                  "requiresApproval": true
                }

                当前工作区：
                - 名称：%s
                - 根目录：%s
                """.formatted(context.getWorkspace().getName(), context.getWorkspace().getRootPath());
    }

    private String buildPlannerInput(MultiAgentState state) {
        RuntimeContext context = state.getRuntimeContext();
        StringBuilder builder = new StringBuilder();
        builder.append("用户任务：").append(state.getTask()).append("\n\n");
        builder.append("最近会话消息数量：").append(context.getRecentMessages().size()).append("\n");
        builder.append("可用长期记忆数量：").append(context.getActiveMemories().size()).append("\n");
        builder.append("请输出一个适合 Coding Agent 执行的结构化计划。");
        return builder.toString();
    }

    private void recordPlannerEvent(MultiAgentState state, PlannerTrace trace, AgentEvent event) {
        if (event instanceof ModelCallStartEvent) {
            emit(state, RuntimeEventType.MODEL_CALL_STARTED, "Planner 调用大模型", null, Map.of("node", nodeName()));
        } else if (event instanceof ModelCallEndEvent modelCallEndEvent) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("node", nodeName());
            if (modelCallEndEvent.getUsage() != null) {
                metadata.put("inputTokens", modelCallEndEvent.getUsage().getInputTokens());
                metadata.put("outputTokens", modelCallEndEvent.getUsage().getOutputTokens());
                metadata.put("totalTokens", modelCallEndEvent.getUsage().getTotalTokens());
            }
            emit(state, RuntimeEventType.MODEL_CALL_FINISHED, "Planner 大模型调用完成", null, metadata);
        } else if (event instanceof TextBlockDeltaEvent textBlockDeltaEvent) {
            trace.append(textBlockDeltaEvent.getDelta());
        }
    }

    private AgentPlan parsePlan(String rawPlan) {
        if (!StringUtils.hasText(rawPlan)) {
            return null;
        }
        try {
            return objectMapper.readValue(stripCodeFence(rawPlan), AgentPlan.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private AgentPlan fallbackPlan(String task) {
        AgentPlan plan = new AgentPlan();
        plan.setTitle("执行计划：" + abbreviate(task, 40));
        plan.setSummary("先收集项目证据，再执行最小修改，最后审查结果。");
        plan.setRiskLevel("MEDIUM");
        plan.setExpectedTools(List.of("LS", "Grep", "Read"));
        plan.setRequiresApproval(true);

        List<AgentPlanStep> steps = new ArrayList<>();
        steps.add(step("1", "定位相关代码", "使用 LS/Grep/Glob 找到和任务相关的入口、配置和调用链。", List.of("LS", "Grep", "Glob")));
        steps.add(step("2", "读取关键文件", "读取目标文件和调用方，确认当前实现，不凭猜测下结论。", List.of("Read")));
        steps.add(step("3", "制定修改方案", "基于证据确定最小修改范围，并识别需要审批的高风险工具。", List.of()));
        steps.add(step("4", "执行并审查", "执行计划后检查 diff、工具结果和风险点。", List.of("Edit", "apply_patch", "Bash")));
        plan.setSteps(steps);
        plan.setAcceptanceCriteria(List.of("计划步骤有明确顺序", "修改前先读取目标文件", "高风险工具需要平台审批", "最终输出说明验证方式"));
        return plan;
    }

    private AgentPlanStep step(String id, String title, String description, List<String> tools) {
        AgentPlanStep step = new AgentPlanStep();
        step.setId(id);
        step.setTitle(title);
        step.setDescription(description);
        step.setStatus("pending");
        step.setAgentName("ExecutorAgent");
        step.setTools(tools);
        return step;
    }

    private void normalizePlan(AgentPlan plan, String task) {
        if (!StringUtils.hasText(plan.getTitle())) {
            plan.setTitle("执行计划：" + abbreviate(task, 40));
        }
        if (!StringUtils.hasText(plan.getSummary())) {
            plan.setSummary("PlannerAgent 已生成结构化执行计划。");
        }
        if (!List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(plan.getRiskLevel())) {
            plan.setRiskLevel("MEDIUM");
        }
        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            plan.setSteps(fallbackPlan(task).getSteps());
        }
        for (int i = 0; i < plan.getSteps().size(); i++) {
            AgentPlanStep step = plan.getSteps().get(i);
            if (!StringUtils.hasText(step.getId())) {
                step.setId(String.valueOf(i + 1));
            }
            if (!StringUtils.hasText(step.getTitle())) {
                step.setTitle("步骤 " + step.getId());
            }
            step.setStatus("pending");
            if (!StringUtils.hasText(step.getAgentName())) {
                step.setAgentName("ExecutorAgent");
            }
            if (step.getTools() == null) {
                step.setTools(List.of());
            }
        }
        if (plan.getAcceptanceCriteria() == null) {
            plan.setAcceptanceCriteria(List.of());
        }
        if (plan.getExpectedTools() == null) {
            plan.setExpectedTools(List.of());
        }
    }

    private void emitPlanCreated(MultiAgentState state, AgentPlan plan) {
        emit(state, RuntimeEventType.PLAN_CREATED, "计划已生成", plan.getSummary(), Map.of("plan", plan));
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

    private String normalizeModelBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String suffix = "/chat/completions";
        if (normalized.endsWith(suffix)) {
            normalized = normalized.substring(0, normalized.length() - suffix.length());
        }
        return normalized;
    }

    private long plannerTimeoutSeconds(RuntimeContext context) {
        int timeoutSeconds = context.getTimeoutSeconds();
        if (timeoutSeconds <= 0) {
            return 120;
        }
        return Math.min(timeoutSeconds, 120);
    }

    private String abbreviate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private static class PlannerTrace {

        private final StringBuilder answer = new StringBuilder();

        private void append(String delta) {
            if (delta != null) {
                answer.append(delta);
            }
        }

        private String answer() {
            return answer.toString();
        }
    }
}
