package com.agentplatform.runtime.agentscope;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.ConversationMessageEntity;
import com.agentplatform.persistence.repository.PatchFileRepository;
import com.agentplatform.persistence.repository.PatchRepository;
import com.agentplatform.runtime.model.AgentRunResult;
import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEventSink;
import com.agentplatform.runtime.tool.CodingAgentWebSearchTools;
import com.agentplatform.runtime.tool.CodingAgentWorkspaceTools;
import com.agentplatform.sandbox.PatchApplyService;
import com.agentplatform.sandbox.SandboxPathResolver;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * AgentScope 运行时适配器。
 * 这个类是智能体运行时模块中唯一直接接触 AgentScope SDK 的入口。
 */
@Component
public class AgentScopeRuntimeAdapter {

    @Resource
    private SandboxPathResolver sandboxPathResolver;

    @Resource
    private PatchRepository patchRepository;

    @Resource
    private PatchFileRepository patchFileRepository;

    @Resource
    private PatchApplyService patchApplyService;

    public AgentRunResult execute(RuntimeContext context, RuntimeEventSink sink) {
        validateModel(context);

        Toolkit toolkit = new Toolkit();
        CodingAgentWorkspaceTools workspaceTools =
                new CodingAgentWorkspaceTools(context, sandboxPathResolver, patchRepository, patchFileRepository, patchApplyService);
        toolkit.registerTool(workspaceTools);
        toolkit.registerTool(new CodingAgentWebSearchTools());

        String modelBaseUrl = normalizeModelBaseUrl(context.getModelBaseUrl());
        OpenAIChatModel.Builder modelBuilder = OpenAIChatModel.builder()
                .baseUrl(modelBaseUrl)
                .modelName(context.getModelName())
                .stream(true);
        if (StringUtils.hasText(context.getApiKey())) {
            modelBuilder.apiKey(context.getApiKey());
        }

        try (ReActAgent agent = ReActAgent.builder()
                .name("coding-agent")
                .description("带工作区沙箱工具的编码智能体")
                .sysPrompt(context.getSystemPrompt())
                .model(modelBuilder.build())
                .toolkit(toolkit)
                .maxIters(context.getMaxIterations())
                .build()) {

            AgentScopeTraceRecorder recorder = new AgentScopeTraceRecorder(context, sink);
            List<Msg> inputMessages = buildInputMessages(context);
            agent.streamEvents(inputMessages)
                    .doOnNext(recorder::record)
                    .collectList()
                    .block(Duration.ofSeconds(context.getTimeoutSeconds()));

            AgentRunResult result = new AgentRunResult();
            result.setRunId(context.getRunId());
            result.setConversationId(context.getConversationId());
            result.setTraceId(context.getTraceId());
            result.setAnswer(recorder.answer());
            result.setInputTokens(recorder.inputTokensOrEstimate(inputText(context)));
            result.setOutputTokens(recorder.outputTokensOrEstimate(recorder.answer()));
            result.setModelCallCount(recorder.getModelCallCount());
            result.setStatus("COMPLETED");
            return result;
        } catch (Exception e) {
            throw new RuntimeException("AgentScope 执行失败：" + e.getMessage(), e);
        }
    }

    private void validateModel(RuntimeContext context) {
        if (!StringUtils.hasText(context.getModelBaseUrl())) {
            throw new BusinessException(400, "模型地址 baseUrl 不能为空");
        }
        if (!StringUtils.hasText(context.getModelName())) {
            throw new BusinessException(400, "模型名称不能为空");
        }
    }

    private String normalizeModelBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String chatCompletionsSuffix = "/chat/completions";
        if (normalized.endsWith(chatCompletionsSuffix)) {
            // AgentScope/OpenAI SDK 需要基础地址，不能传完整的 chat-completions 接口地址。
            normalized = normalized.substring(0, normalized.length() - chatCompletionsSuffix.length());
        }
        return normalized;
    }
    private List<Msg> buildInputMessages(RuntimeContext context) {
        List<Msg> messages = new ArrayList<>();
        for (ConversationMessageEntity message : context.getRecentMessages()) {
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            if ("USER".equalsIgnoreCase(message.getRole())) {
                messages.add(new UserMessage(message.getContent()));
            } else if ("ASSISTANT".equalsIgnoreCase(message.getRole())) {
                messages.add(new AssistantMessage(message.getContent()));
            }
        }
        if (messages.isEmpty()) {
            messages.add(new UserMessage(context.getCommand().getMessage()));
        }
        return messages;
    }

    private String inputText(RuntimeContext context) {
        StringBuilder sb = new StringBuilder();
        for (ConversationMessageEntity message : context.getRecentMessages()) {
            if (message != null && message.getContent() != null) {
                sb.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
            }
        }
        return sb.toString();
    }
}
