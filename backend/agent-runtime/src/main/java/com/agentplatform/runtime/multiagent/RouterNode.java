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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RouterAgent。
 * 中文注释：只做任务分流，不执行工具、不修改文件、不生成最终答案。
 */
@Component
public class RouterNode implements AgentNode {

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public String nodeName() {
        return "RouterAgent";
    }

    @Override
    public AgentNodeResult invoke(MultiAgentState state) {
        AgentRouteDecision decision = route(state);
        return AgentNodeResult.success("RouterAgent 选择路由：" + decision.effectiveRoute());
    }

    public AgentRouteDecision route(MultiAgentState state) {
        state.setCurrentNode(nodeName());
        emit(state, RuntimeEventType.AGENT_HANDOFF, "切换 Agent：Router",
                "RouterAgent 开始判断任务应该走哪条流程", Map.of("node", nodeName()));

        String rawDecision = "";
        try (ReActAgent agent = buildRouterAgent(state.getRuntimeContext())) {
            RouterTrace trace = new RouterTrace();
            agent.streamEvents(List.of(new UserMessage(buildRouterInput(state))))
                    .doOnNext(event -> recordRouterEvent(state, trace, event))
                    .collectList()
                    .block(Duration.ofSeconds(routerTimeoutSeconds(state.getRuntimeContext())));
            rawDecision = trace.answer().trim();
        } catch (Exception e) {
            emit(state, RuntimeEventType.RUNTIME_WARNING, "Router 降级",
                    "RouterAgent 调用失败，已使用规则路由兜底：" + e.getMessage(), Map.of("node", nodeName()));
        }

        AgentRouteDecision decision = parseDecision(rawDecision);
        if (decision == null) {
            decision = fallbackDecision(state.getTask());
        }
        normalizeDecision(decision, state.getTask());
        return decision;
    }

    private ReActAgent buildRouterAgent(RuntimeContext context) {
        return ReActAgent.builder()
                .name("router-agent")
                .description("只负责选择智能体流程的路由智能体")
                .sysPrompt(buildRouterPrompt(context))
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

    private String buildRouterPrompt(RuntimeContext context) {
        return """
                你是 RouterAgent，只负责为用户任务选择执行流程。

                你不能执行工具，不能修改文件，不能生成最终答案。
                你只能输出严格 JSON，不要 Markdown，不要代码块，不要解释。

                可选 route：
                - DIRECT_ANSWER：通用解释、学习讨论、概念问题、面试问答，不需要读取工作区。
                - SINGLE_AGENT：需要读取工作区或调查现象，但用户没有明确要求改代码。
                - PLAN_ONLY：用户明确要求先给计划、方案、设计，不要执行。
                - PLAN_EXECUTE：用户要求实现、修复、修改、重构、接入、删除、生成文件或执行编码任务。

                规则：
                1. 显式 slash command 已由平台提前处理，你这里只处理普通用户输入。
                2. 只要用户要求修改代码或创建文件，优先 PLAN_EXECUTE。
                3. 用户说“先看看、解释、分析原因、为什么报错”，但没有要求修复，选 SINGLE_AGENT。
                4. 用户问通用概念、架构思想、学习路线，选 DIRECT_ANSWER。
                5. 不确定时选 SINGLE_AGENT，不要冒进执行。
                6. 高风险、需要多文件修改、删除、Bash 的任务 riskLevel 至少 MEDIUM。

                JSON Schema：
                {
                  "route": "DIRECT_ANSWER|SINGLE_AGENT|PLAN_ONLY|PLAN_EXECUTE",
                  "intent": "DIRECT_ANSWER|WORKSPACE_QA|CODE_CHANGE|DEBUG|PLAN|SEARCH|GENERAL",
                  "reason": "一句话说明为什么这样路由",
                  "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                  "confidence": 0.0,
                  "requiresWorkspaceEvidence": true,
                  "requiresReview": false
                }

                当前工作区：
                - 名称：%s
                - 根目录：%s
                """.formatted(context.getWorkspace().getName(), context.getWorkspace().getRootPath());
    }

    private String buildRouterInput(MultiAgentState state) {
        RuntimeContext context = state.getRuntimeContext();
        StringBuilder builder = new StringBuilder();
        builder.append("用户任务：").append(state.getTask()).append("\n\n");
        builder.append("最近会话消息数量：").append(context.getRecentMessages().size()).append("\n");
        builder.append("可用长期记忆数量：").append(context.getActiveMemories().size()).append("\n");
        builder.append("请判断下一步应该走哪条 route。");
        return builder.toString();
    }

    private void recordRouterEvent(MultiAgentState state, RouterTrace trace, AgentEvent event) {
        if (event instanceof ModelCallStartEvent) {
            emit(state, RuntimeEventType.MODEL_CALL_STARTED, "Router 调用大模型", null, Map.of("node", nodeName()));
        } else if (event instanceof ModelCallEndEvent modelCallEndEvent) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("node", nodeName());
            if (modelCallEndEvent.getUsage() != null) {
                metadata.put("inputTokens", modelCallEndEvent.getUsage().getInputTokens());
                metadata.put("outputTokens", modelCallEndEvent.getUsage().getOutputTokens());
                metadata.put("totalTokens", modelCallEndEvent.getUsage().getTotalTokens());
            }
            emit(state, RuntimeEventType.MODEL_CALL_FINISHED, "Router 大模型调用完成", null, metadata);
        } else if (event instanceof TextBlockDeltaEvent textBlockDeltaEvent) {
            trace.append(textBlockDeltaEvent.getDelta());
        }
    }

    private AgentRouteDecision parseDecision(String rawDecision) {
        if (!StringUtils.hasText(rawDecision)) {
            return null;
        }
        try {
            return objectMapper.readValue(stripCodeFence(rawDecision), AgentRouteDecision.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private AgentRouteDecision fallbackDecision(String task) {
        String text = task == null ? "" : task.toLowerCase();
        AgentRouteDecision decision = new AgentRouteDecision();
        decision.setReason("RouterAgent 未返回有效 JSON，使用规则路由兜底");
        decision.setConfidence(0.45);

        if (containsAny(text, "计划", "方案", "设计一下", "先说思路", "先别改", "不要改")) {
            decision.setRoute(AgentRouteDecision.ROUTE_PLAN_ONLY);
            decision.setIntent("PLAN");
            decision.setRiskLevel("LOW");
            return decision;
        }
        if (containsAny(text, "实现", "修复", "修改", "重构", "新增", "删除", "接入", "生成", "改一下", "优化")) {
            decision.setRoute(AgentRouteDecision.ROUTE_PLAN_EXECUTE);
            decision.setIntent("CODE_CHANGE");
            decision.setRiskLevel("MEDIUM");
            decision.setRequiresWorkspaceEvidence(true);
            decision.setRequiresReview(true);
            return decision;
        }
        if (containsAny(text, "这个项目", "这个接口", "这个文件", "报错", "为什么", "看下", "检查")) {
            decision.setRoute(AgentRouteDecision.ROUTE_SINGLE_AGENT);
            decision.setIntent("WORKSPACE_QA");
            decision.setRiskLevel("LOW");
            decision.setRequiresWorkspaceEvidence(true);
            return decision;
        }

        decision.setRoute(AgentRouteDecision.ROUTE_DIRECT_ANSWER);
        decision.setIntent("DIRECT_ANSWER");
        decision.setRiskLevel("LOW");
        return decision;
    }

    private void normalizeDecision(AgentRouteDecision decision, String task) {
        if (StringUtils.hasText(decision.getRoute())) {
            decision.setRoute(decision.getRoute().trim().toUpperCase());
        }
        if (StringUtils.hasText(decision.getRiskLevel())) {
            decision.setRiskLevel(decision.getRiskLevel().trim().toUpperCase());
        }
        if (!List.of(
                AgentRouteDecision.ROUTE_DIRECT_ANSWER,
                AgentRouteDecision.ROUTE_SINGLE_AGENT,
                AgentRouteDecision.ROUTE_PLAN_ONLY,
                AgentRouteDecision.ROUTE_PLAN_EXECUTE
        ).contains(decision.getRoute())) {
            decision.setRoute(AgentRouteDecision.ROUTE_SINGLE_AGENT);
        }
        if (!List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(decision.getRiskLevel())) {
            decision.setRiskLevel("LOW");
        }
        if (decision.getConfidence() < 0 || decision.getConfidence() > 1) {
            decision.setConfidence(0.5);
        }
        if (!StringUtils.hasText(decision.getIntent())) {
            decision.setIntent("GENERAL");
        }
        if (!StringUtils.hasText(decision.getReason())) {
            decision.setReason("根据用户任务自动选择路由：" + abbreviate(task, 40));
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

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
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

    private long routerTimeoutSeconds(RuntimeContext context) {
        int timeoutSeconds = context.getTimeoutSeconds();
        if (timeoutSeconds <= 0) {
            return 60;
        }
        return Math.min(timeoutSeconds, 60);
    }

    private String abbreviate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private static class RouterTrace {

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
