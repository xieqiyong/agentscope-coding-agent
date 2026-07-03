package com.agentplatform.runtime.agentscope;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.ConversationMessageEntity;
import com.agentplatform.persistence.repository.PatchFileRepository;
import com.agentplatform.persistence.repository.PatchRepository;
import com.agentplatform.runtime.model.AgentRunResult;
import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventSink;
import com.agentplatform.runtime.model.RuntimeEventType;
import com.agentplatform.runtime.service.ToolApprovalService;
import com.agentplatform.runtime.service.RuntimeToolGuard;
import com.agentplatform.runtime.tool.CodingAgentCommandTools;
import com.agentplatform.runtime.tool.CodingAgentWebSearchTools;
import com.agentplatform.runtime.tool.CodingAgentWorkspaceTools;
import com.agentplatform.sandbox.CommandSandboxService;
import com.agentplatform.sandbox.PatchApplyService;
import com.agentplatform.sandbox.SandboxPathResolver;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.tool.ToolSuspendException;
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
 * AgentScope 运行时适配器。
 * 这个类只负责把平台上下文转换成 AgentScope 调用，平台自己的沙箱、记忆、审计仍然由外层模块控制。
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

    @Resource
    private CommandSandboxService commandSandboxService;

    @Resource
    private RuntimeToolGuard runtimeToolGuard;

    @Resource
    private AgentScopeSessionManager sessionManager;

    @Resource
    private AgentScopePermissionContextFactory permissionContextFactory;

    public AgentRunResult execute(RuntimeContext context, RuntimeEventSink sink) {
        validateModel(context);
        long startedNanos = System.nanoTime();

        emitSessionBindingStarted(context, sink);
        AgentScopeSessionBinding sessionBinding = sessionManager.bind(context);
        emitSessionLoaded(context, sink, sessionBinding, elapsedMs(startedNanos));

        AgentScopeTraceRecorder recorder = new AgentScopeTraceRecorder(context, sink);
        try (ReActAgent agent = buildAgent(context, sessionBinding)) {
            List<Msg> inputMessages = buildInputMessages(context);
            agent.streamEvents(inputMessages)
                    .doOnNext(recorder::record)
                    .collectList()
                    .block(Duration.ofSeconds(context.getTimeoutSeconds()));

            return buildResult(context, recorder, inputText(context));
        } catch (Exception e) {
            if (context.isPlatformApprovalRequired() || hasCause(e, ToolSuspendException.class)) {
                return buildInterruptedResult(context, recorder, inputText(context));
            }
            throw new RuntimeException("AgentScope 执行失败: " + e.getMessage(), e);
        }
    }

    public AgentRunResult executeDirectAnswer(RuntimeContext context, RuntimeEventSink sink) {
        validateModel(context);
        AgentScopeTraceRecorder recorder = new AgentScopeTraceRecorder(context, sink);
        try (ReActAgent agent = buildDirectAnswerAgent(context)) {
            List<Msg> inputMessages = buildDirectAnswerMessages(context);
            agent.streamEvents(inputMessages)
                    .doOnNext(recorder::record)
                    .collectList()
                    .block(Duration.ofSeconds(directAnswerTimeoutSeconds(context)));

            return buildResult(context, recorder, directAnswerInputText(context));
        } catch (Exception e) {
            throw new RuntimeException("DirectAnswerAgent 执行失败: " + e.getMessage(), e);
        }
    }

    public AgentRunResult resumeAfterApproval(RuntimeContext context,
                                              ToolApprovalService.ToolApprovalPayload payload,
                                              boolean approved,
                                              RuntimeEventSink sink) {
        validateModel(context);
        long startedNanos = System.nanoTime();

        emitSessionBindingStarted(context, sink);
        AgentScopeSessionBinding sessionBinding = sessionManager.bind(context);
        if (!sessionBinding.isEnabled()) {
            throw new BusinessException(409, "AgentScope Session 未启用，无法恢复等待审批的工具调用");
        }
        emitSessionLoaded(context, sink, sessionBinding, elapsedMs(startedNanos));

        try (ReActAgent agent = buildAgent(context, sessionBinding)) {
            AgentScopeTraceRecorder recorder = new AgentScopeTraceRecorder(context, sink);
            List<Msg> inputMessages = buildConfirmMessages(payload, approved);
            agent.streamEvents(inputMessages)
                    .doOnNext(recorder::record)
                    .collectList()
                    .block(Duration.ofSeconds(context.getTimeoutSeconds()));

            return buildResult(context, recorder, "CONFIRM_RESULT: " + approved);
        } catch (Exception e) {
            throw new RuntimeException("AgentScope 审批恢复失败: " + e.getMessage(), e);
        }
    }

    private ReActAgent buildAgent(RuntimeContext context, AgentScopeSessionBinding sessionBinding) {
        PermissionContextState permissionContext = permissionContextFactory.build();
        ReActAgent.Builder agentBuilder = ReActAgent.builder()
                .name("coding-agent")
                .description("带工作区沙箱工具的编码智能体")
                .sysPrompt(context.getSystemPrompt())
                .model(buildModel(context))
                .toolkit(buildToolkit(context))
                .permissionContext(permissionContext)
                .maxIters(context.getMaxIterations());

        if (sessionBinding.isEnabled()) {
            agentBuilder.stateStore(sessionBinding.getStateStore())
                    .defaultSessionId(sessionBinding.getSessionId())
                    .enablePendingToolRecovery(true);
        }
        ReActAgent agent = agentBuilder.build();
        applyRuntimePermissionRules(agent, permissionContext);
        return agent;
    }

    private ReActAgent buildDirectAnswerAgent(RuntimeContext context) {
        return ReActAgent.builder()
                .name("direct-answer-agent")
                .description("无需工作区工具的直接回答智能体")
                .sysPrompt(buildDirectAnswerPrompt(context))
                .model(buildModel(context))
                .toolkit(new Toolkit())
                .maxIters(1)
                .build();
    }

    private void applyRuntimePermissionRules(ReActAgent agent, PermissionContextState permissionContext) {
        if (agent == null || permissionContext == null) {
            return;
        }
        for (List<PermissionRule> rules : permissionContext.getAskRules().values()) {
            for (PermissionRule rule : rules) {
                agent.getPermissionEngine().addRule(rule);
            }
        }
        for (List<PermissionRule> rules : permissionContext.getDenyRules().values()) {
            for (PermissionRule rule : rules) {
                agent.getPermissionEngine().addRule(rule);
            }
        }
        for (List<PermissionRule> rules : permissionContext.getAllowRules().values()) {
            for (PermissionRule rule : rules) {
                agent.getPermissionEngine().addRule(rule);
            }
        }
    }

    private Toolkit buildToolkit(RuntimeContext context) {
        Toolkit toolkit = new Toolkit();
        CodingAgentWorkspaceTools workspaceTools =
                new CodingAgentWorkspaceTools(context, sandboxPathResolver, patchRepository, patchFileRepository, patchApplyService);
        toolkit.registerTool(workspaceTools);
        toolkit.registerTool(new CodingAgentCommandTools(context, commandSandboxService, runtimeToolGuard));
        toolkit.registerTool(new CodingAgentWebSearchTools());
        return toolkit;
    }

    private OpenAIChatModel buildModel(RuntimeContext context) {
        String modelBaseUrl = normalizeModelBaseUrl(context.getModelBaseUrl());
        OpenAIChatModel.Builder modelBuilder = OpenAIChatModel.builder()
                .baseUrl(modelBaseUrl)
                .modelName(context.getModelName())
                .stream(true);
        if (StringUtils.hasText(context.getApiKey())) {
            modelBuilder.apiKey(context.getApiKey());
        }
        return modelBuilder.build();
    }

    private List<Msg> buildConfirmMessages(ToolApprovalService.ToolApprovalPayload payload, boolean approved) {
        ConfirmResult confirmResult = new ConfirmResult(approved, payload.toolCall());
        Msg message = UserMessage.builder()
                .textContent(approved ? "用户已批准上一次工具调用。" : "用户已拒绝上一次工具调用。")
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, List.of(confirmResult)))
                .build();
        return List.of(message);
    }

    private AgentRunResult buildResult(RuntimeContext context, AgentScopeTraceRecorder recorder, String inputText) {
        AgentRunResult result = new AgentRunResult();
        result.setRunId(context.getRunId());
        result.setConversationId(context.getConversationId());
        result.setTraceId(context.getTraceId());
        result.setAnswer(recorder.answer());
        result.setInputTokens(recorder.inputTokensOrEstimate(inputText));
        result.setOutputTokens(recorder.outputTokensOrEstimate(recorder.answer()));
        result.setModelCallCount(recorder.getModelCallCount());
        result.setStatus(recorder.isConfirmationRequired() || context.isPlatformApprovalRequired()
                ? "WAITING_APPROVAL"
                : "COMPLETED");
        return result;
    }

    private AgentRunResult buildInterruptedResult(RuntimeContext context,
                                                  AgentScopeTraceRecorder recorder,
                                                  String inputText) {
        AgentRunResult result = buildResult(context, recorder, inputText);
        result.setStatus("WAITING_APPROVAL");
        return result;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> expectedType) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
        if (context.isAgentScopeSessionEnabled() && context.isAgentScopeStateExists()) {
            return currentUserMessageOnly(context);
        }

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

    private List<Msg> buildDirectAnswerMessages(RuntimeContext context) {
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

    private String buildDirectAnswerPrompt(RuntimeContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                你是 DirectAnswerAgent，负责回答不需要读取工作区、不需要执行工具的普通问题。
                要求：
                1. 默认使用中文回答，表达直接、清晰、简洁。
                2. 不要声称已经读取文件、执行命令或检查工作区。
                3. 如果用户问题必须依赖项目文件或运行结果，请说明需要切换到工作区分析流程。
                """);
        if (context.getActiveMemories() != null && !context.getActiveMemories().isEmpty()) {
            builder.append("\n可参考的长期记忆：\n");
            context.getActiveMemories().stream()
                    .limit(8)
                    .forEach(memory -> builder
                            .append("- ")
                            .append(safe(memory.getMemoryType()))
                            .append(": ")
                            .append(safe(memory.getContent()))
                            .append("\n"));
        }
        return builder.toString();
    }

    private List<Msg> currentUserMessageOnly(RuntimeContext context) {
        List<Msg> messages = new ArrayList<>();
        messages.add(new UserMessage(context.getCommand().getMessage()));
        return messages;
    }

    private void emitSessionBindingStarted(RuntimeContext context, RuntimeEventSink sink) {
        sink.emit(RuntimeEvent.of(context.getRunId(), context.getTraceId(), RuntimeEventType.RUN_STATUS_CHANGED,
                "AgentScope 状态绑定中", "正在连接并检查 AgentScope 状态存储",
                Map.of("sessionType", safe(context.getAgentScopeSessionType())), elapsedMs(context.getRunStartedNanos())));
    }

    private void emitSessionLoaded(RuntimeContext context, RuntimeEventSink sink,
                                   AgentScopeSessionBinding binding, long elapsedMs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("enabled", binding.isEnabled());
        metadata.put("type", binding.getType());
        metadata.put("stateExists", binding.isStateExists());
        metadata.put("sessionKey", context.getAgentScopeSessionKey());
        metadata.put("inputStrategy", binding.isEnabled() && binding.isStateExists()
                ? "CURRENT_MESSAGE_ONLY"
                : "DATABASE_HISTORY_BOOTSTRAP");

        String content = binding.isEnabled()
                ? "已绑定 AgentScope Session，内部 AgentState 将由 " + binding.getType() + " 恢复和保存"
                : "未启用 AgentScope Session，本轮只使用数据库外部上下文";
        sink.emit(RuntimeEvent.of(context.getRunId(), context.getTraceId(), RuntimeEventType.CONTEXT_LOADED,
                "AgentScope 状态加载完成", content, metadata, elapsedMs));
    }

    private String inputText(RuntimeContext context) {
        if (context.isAgentScopeSessionEnabled() && context.isAgentScopeStateExists()) {
            return "USER: " + context.getCommand().getMessage();
        }

        StringBuilder sb = new StringBuilder();
        for (ConversationMessageEntity message : context.getRecentMessages()) {
            if (message != null && message.getContent() != null) {
                sb.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    private String directAnswerInputText(RuntimeContext context) {
        StringBuilder sb = new StringBuilder();
        for (ConversationMessageEntity message : context.getRecentMessages()) {
            if (message != null && message.getContent() != null) {
                sb.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
            }
        }
        if (sb.length() == 0) {
            sb.append("USER: ").append(context.getCommand().getMessage());
        }
        return sb.toString();
    }

    private long directAnswerTimeoutSeconds(RuntimeContext context) {
        int timeoutSeconds = context.getTimeoutSeconds();
        if (timeoutSeconds <= 0) {
            return 60;
        }
        return Math.min(timeoutSeconds, 60);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long elapsedMs(long startedNanos) {
        if (startedNanos <= 0) {
            return 0;
        }
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
