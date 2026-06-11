package com.agentplatform.runtime.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.AgentRunEntity;
import com.agentplatform.persistence.entity.ConversationEntity;
import com.agentplatform.persistence.entity.ConversationMessageEntity;
import com.agentplatform.persistence.repository.AgentRunRepository;
import com.agentplatform.persistence.repository.ConversationMessageRepository;
import com.agentplatform.persistence.repository.ConversationRepository;
import com.agentplatform.runtime.agentscope.AgentScopeRuntimeAdapter;
import com.agentplatform.runtime.model.AgentRunCommand;
import com.agentplatform.runtime.model.AgentRunResult;
import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventSink;
import com.agentplatform.runtime.model.RuntimeEventType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 智能体运行时总入口。
 * 它负责一次用户请求的生命周期：建会话消息、建 run、调用 AgentScope 执行智能体循环、落库事件、保存回答。
 */
@Service
public class AgentRuntimeService {

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private ConversationMessageRepository conversationMessageRepository;

    @Resource
    private AgentRunRepository agentRunRepository;

    @Resource
    private AgentRunContextBuilder contextBuilder;

    @Resource
    private AgentRunTraceService traceService;

    @Resource
    private AgentScopeRuntimeAdapter agentScopeRuntimeAdapter;

    public AgentRunResult executeStreaming(AgentRunCommand command, RuntimeEventSink clientSink) {
        validateCommand(command);
        long started = System.nanoTime();
        String traceId = UUID.randomUUID().toString();

        ConversationEntity conversation = resolveConversation(command);
        command.setConversationId(conversation.getId());
        command.setWorkspaceId(conversation.getWorkspaceId());
        if (command.getAgentId() == null && conversation.getAgentId() != null) {
            command.setAgentId(conversation.getAgentId());
        }

        ConversationMessageEntity userMessage = saveUserMessage(conversation.getId(), command.getMessage());
        AgentRunEntity run = startRun(command, conversation.getId(), userMessage.getId(), traceId);

        RuntimeEventSink persistedSink = event -> traceService.recordAndForward(event, clientSink);
        emit(persistedSink, run.getId(), traceId, RuntimeEventType.RUN_STARTED, "运行开始",
                "智能体运行已开始", Map.of("conversationId", conversation.getId()), elapsedMs(started));

        try {
            RuntimeContext context = contextBuilder.build(command, conversation.getId(), userMessage.getId(), run.getId(), traceId);
            emit(persistedSink, run.getId(), traceId, RuntimeEventType.CONTEXT_LOADED, "上下文加载完成",
                    null, Map.of(
                            "workspaceId", context.getWorkspace().getId(),
                            "agentId", context.getAgent().getId(),
                            "messageCount", context.getRecentMessages().size(),
                            "memoryCount", context.getActiveMemories().size(),
                            "model", safe(context.getModelName())
                    ), elapsedMs(started));

            AgentRunResult result = agentScopeRuntimeAdapter.execute(context, persistedSink);
            saveAssistantMessage(conversation.getId(), result.getAnswer());
            finishRun(run, "COMPLETED", result, null);
            emit(persistedSink, run.getId(), traceId, RuntimeEventType.RUN_FINISHED, "运行完成",
                    "智能体运行已完成", Map.of(
                            "inputTokens", result.getInputTokens(),
                            "outputTokens", result.getOutputTokens(),
                            "modelCallCount", result.getModelCallCount()
                    ), elapsedMs(started));
            result.setStatus("COMPLETED");
            return result;
        } catch (Exception e) {
            finishRun(run, "FAILED", null, e.getMessage());
            emit(persistedSink, run.getId(), traceId, RuntimeEventType.RUN_ERROR, "运行异常",
                    e.getMessage(), Map.of(), elapsedMs(started));
            throw e;
        }
    }

    private void validateCommand(AgentRunCommand command) {
        if (command == null) {
            throw new BusinessException(400, "缺少智能体运行命令");
        }
        if (!StringUtils.hasText(command.getMessage())) {
            throw new BusinessException(400, "用户消息不能为空");
        }
        if (command.getConversationId() == null && command.getWorkspaceId() == null) {
            throw new BusinessException(400, "新建会话时必须传工作区 ID（workspaceId）");
        }
        if (command.getAgentId() == null && command.getConversationId() == null) {
            throw new BusinessException(400, "新建会话时必须传智能体 ID（agentId）");
        }
        if (!StringUtils.hasText(command.getUserId())) {
            command.setUserId("default");
        }
    }

    private ConversationEntity resolveConversation(AgentRunCommand command) {
        if (command.getConversationId() != null) {
            return conversationRepository.findById(command.getConversationId())
                    .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        }
        ConversationEntity conversation = new ConversationEntity();
        conversation.setWorkspaceId(command.getWorkspaceId());
        conversation.setAgentId(command.getAgentId());
        conversation.setTitle(StringUtils.hasText(command.getTitle()) ? command.getTitle() : defaultTitle(command.getMessage()));
        conversation.setStatus("ACTIVE");
        return conversationRepository.save(conversation);
    }

    private ConversationMessageEntity saveUserMessage(Long conversationId, String message) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setConversationId(conversationId);
        entity.setRole("USER");
        entity.setContent(message);
        entity.setTokenCount(estimateTokens(message));
        entity.setMetadataJson("{}");
        return conversationMessageRepository.save(entity);
    }

    private void saveAssistantMessage(Long conversationId, String answer) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setConversationId(conversationId);
        entity.setRole("ASSISTANT");
        entity.setContent(StringUtils.hasText(answer) ? answer : "");
        entity.setTokenCount(estimateTokens(answer));
        entity.setMetadataJson("{}");
        conversationMessageRepository.save(entity);
    }

    private AgentRunEntity startRun(AgentRunCommand command, Long conversationId, Long userMessageId, String traceId) {
        AgentRunEntity run = new AgentRunEntity();
        run.setTraceId(traceId);
        run.setConversationId(conversationId);
        run.setAgentId(command.getAgentId());
        run.setWorkspaceId(command.getWorkspaceId());
        run.setUserMessageId(userMessageId);
        run.setStatus("RUNNING");
        run.setInputTokens(0);
        run.setOutputTokens(0);
        run.setStartedAt(LocalDateTime.now());
        return agentRunRepository.save(run);
    }

    private void finishRun(AgentRunEntity run, String status, AgentRunResult result, String errorMessage) {
        run.setStatus(status);
        run.setFinishedAt(LocalDateTime.now());
        run.setErrorMessage(errorMessage);
        if (result != null) {
            run.setInputTokens(result.getInputTokens());
            run.setOutputTokens(result.getOutputTokens());
        }
        agentRunRepository.save(run);
    }

    private void emit(RuntimeEventSink sink, Long runId, String traceId, RuntimeEventType type, String stage,
                      String content, Map<String, Object> metadata, long elapsedMs) {
        sink.emit(RuntimeEvent.of(runId, traceId, type, stage, content, metadata, elapsedMs));
    }

    private String defaultTitle(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= 30 ? trimmed : trimmed.substring(0, 30);
    }

    private int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

