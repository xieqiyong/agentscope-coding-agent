package com.agentplatform.runtime.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.memory.model.MemoryCaptureResult;
import com.agentplatform.memory.service.MemoryCaptureService;
import com.agentplatform.persistence.entity.AgentRunEntity;
import com.agentplatform.persistence.entity.ConversationEntity;
import com.agentplatform.persistence.entity.ConversationMessageEntity;
import com.agentplatform.persistence.enums.AgentRunStatus;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * 智能体运行时总入口。
 * 它负责一次用户请求的生命周期：建会话消息、建 run、调用 AgentScope 执行智能体循环、落库事件、保存回答。
 */
@Service
public class AgentRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeService.class);

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private ConversationMessageRepository conversationMessageRepository;

    @Resource
    private AgentRunContextBuilder contextBuilder;

    @Resource
    private AgentRunTraceService traceService;

    @Resource
    private AgentScopeRuntimeAdapter agentScopeRuntimeAdapter;

    @Resource
    private AgentRunLifecycleService lifecycleService;

    @Resource
    private MemoryCaptureService memoryCaptureService;

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
        AgentRunEntity run = lifecycleService.startRun(command, conversation.getId(), userMessage.getId(), traceId);

        RuntimeEventSink persistedSink = wrapLifecycleSink(run.getId(), traceId, clientSink, started);
        emit(persistedSink, run.getId(), traceId, RuntimeEventType.RUN_STARTED, "运行开始",
                "智能体运行已开始", Map.of(
                        "conversationId", conversation.getId(),
                        "status", AgentRunStatus.RUNNING.name()
                ), elapsedMs(started));

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
            MemoryCaptureResult memoryCaptureResult = captureMemory(command, conversation.getId(), userMessage.getId(), result.getAnswer());
            lifecycleService.completeRun(run.getId(), result);
            emit(persistedSink, run.getId(), traceId, RuntimeEventType.RUN_FINISHED, "运行完成",
                    "智能体运行已完成", Map.of(
                            "status", AgentRunStatus.COMPLETED.name(),
                            "inputTokens", result.getInputTokens(),
                            "outputTokens", result.getOutputTokens(),
                            "modelCallCount", result.getModelCallCount(),
                            "memoryCaptured", memoryCaptureResult.captured(),
                            "memoryActivated", memoryCaptureResult.activated(),
                            "memoryPending", memoryCaptureResult.pending(),
                            "memoryConflicts", memoryCaptureResult.conflicts(),
                            "conversationId", conversation.getId()
                    ), elapsedMs(started));
            result.setStatus("COMPLETED");
            return result;
        } catch (Exception e) {
            AgentRunStatus errorStatus = isTimeout(e) ? AgentRunStatus.TIMEOUT : AgentRunStatus.FAILED;
            if (errorStatus == AgentRunStatus.TIMEOUT) {
                lifecycleService.timeoutRun(run.getId(), e.getMessage());
            } else {
                lifecycleService.failRun(run.getId(), e.getMessage());
            }
            emit(persistedSink, run.getId(), traceId, RuntimeEventType.RUN_ERROR, "运行异常",
                    e.getMessage(), Map.of("status", errorStatus.name()), elapsedMs(started));
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

    private void emit(RuntimeEventSink sink, Long runId, String traceId, RuntimeEventType type, String stage,
                      String content, Map<String, Object> metadata, long elapsedMs) {
        sink.emit(RuntimeEvent.of(runId, traceId, type, stage, content, metadata, elapsedMs));
    }

    private MemoryCaptureResult captureMemory(AgentRunCommand command, Long conversationId,
                                              Long userMessageId, String answer) {
        try {
            return memoryCaptureService.captureAfterRun(
                    command.getWorkspaceId(),
                    command.getAgentId(),
                    command.getUserId(),
                    conversationId,
                    userMessageId,
                    command.getMessage(),
                    answer
            );
        } catch (Exception e) {
            // 中文注释：记忆捕获是旁路能力，失败不能影响本轮 Agent 正常回答和 run 收口。
            log.warn("长期记忆捕获失败，已跳过，conversationId={}，userMessageId={}，原因={}",
                    conversationId, userMessageId, e.getMessage(), e);
            return MemoryCaptureResult.empty();
        }
    }

    private RuntimeEventSink wrapLifecycleSink(Long runId, String traceId, RuntimeEventSink clientSink, long startedNanos) {
        return event -> {
            traceService.recordAndForward(event, clientSink);
            handleLifecycleEvent(runId, traceId, event, clientSink, startedNanos);
        };
    }

    private void handleLifecycleEvent(Long runId, String traceId, RuntimeEvent event,
                                      RuntimeEventSink clientSink, long startedNanos) {
        if (event == null || event.getType() == null) {
            return;
        }
        if (event.getType() == RuntimeEventType.CONFIRMATION_REQUIRED) {
            AgentRunEntity updated = lifecycleService.waitForApproval(runId);
            if (AgentRunStatus.WAITING_APPROVAL.name().equals(updated.getStatus())) {
                traceService.recordAndForward(RuntimeEvent.of(runId, traceId, RuntimeEventType.RUN_STATUS_CHANGED,
                        "状态变更：等待用户确认", "Agent Run 已暂停，等待用户确认后才能继续",
                        Map.of("status", AgentRunStatus.WAITING_APPROVAL.name()), elapsedMs(startedNanos)), clientSink);
            }
        } else if (event.getType() == RuntimeEventType.CONFIRMATION_RESULT) {
            AgentRunEntity updated = lifecycleService.resumeFromApproval(runId);
            if (AgentRunStatus.RUNNING.name().equals(updated.getStatus())) {
                traceService.recordAndForward(RuntimeEvent.of(runId, traceId, RuntimeEventType.RUN_STATUS_CHANGED,
                        "状态变更：继续运行", "用户确认结果已返回，Agent Run 继续执行",
                        Map.of("status", AgentRunStatus.RUNNING.name()), elapsedMs(startedNanos)), clientSink);
            }
        }
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
