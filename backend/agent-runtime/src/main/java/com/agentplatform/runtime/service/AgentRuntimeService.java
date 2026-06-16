package com.agentplatform.runtime.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.memory.model.MemoryCaptureResult;
import com.agentplatform.memory.service.MemoryCaptureService;
import com.agentplatform.persistence.entity.AgentRunEntity;
import com.agentplatform.persistence.entity.ApprovalRequestEntity;
import com.agentplatform.persistence.entity.ConversationEntity;
import com.agentplatform.persistence.entity.ConversationMessageEntity;
import com.agentplatform.persistence.enums.AgentRunStatus;
import com.agentplatform.persistence.repository.AgentRunRepository;
import com.agentplatform.persistence.repository.ConversationMessageRepository;
import com.agentplatform.persistence.repository.ConversationRepository;
import com.agentplatform.runtime.model.AgentApprovalCommand;
import com.agentplatform.runtime.agentscope.AgentScopeRuntimeAdapter;
import com.agentplatform.runtime.model.AgentRunCommand;
import com.agentplatform.runtime.model.AgentRunResult;
import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventSink;
import com.agentplatform.runtime.model.RuntimeEventType;
import com.agentplatform.runtime.multiagent.MultiAgentOrchestrator;
import com.agentplatform.sandbox.CommandExecutionResult;
import com.agentplatform.sandbox.CommandSandboxService;
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
    private AgentRunRepository agentRunRepository;

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

    @Resource
    private ToolApprovalService toolApprovalService;

    @Resource
    private CommandSandboxService commandSandboxService;

    @Resource
    private MultiAgentOrchestrator multiAgentOrchestrator;

    public AgentRunResult executeStreaming(AgentRunCommand command, RuntimeEventSink clientSink) {
        validateCommand(command);
        normalizeRunMode(command);
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
            context.setRuntimeEventSink(persistedSink);
            context.setRunStartedNanos(started);
            emit(persistedSink, run.getId(), traceId, RuntimeEventType.CONTEXT_LOADED, "上下文加载完成",
                    null, Map.of(
                            "workspaceId", context.getWorkspace().getId(),
                            "agentId", context.getAgent().getId(),
                            "messageCount", context.getRecentMessages().size(),
                            "memoryCount", context.getActiveMemories().size(),
                            "model", safe(context.getModelName())
                    ), elapsedMs(started));

            if (isPlanOnly(command)) {
                AgentRunResult result = multiAgentOrchestrator.planOnly(context, persistedSink);
                saveAssistantMessage(conversation.getId(), result.getAnswer());
                lifecycleService.completeRun(run.getId(), result);
                emit(persistedSink, run.getId(), traceId, RuntimeEventType.RUN_FINISHED, "运行完成",
                        "PlannerAgent 已完成计划生成", Map.of(
                                "status", AgentRunStatus.COMPLETED.name(),
                                "conversationId", conversation.getId(),
                                "runMode", "PLAN_ONLY"
                        ), elapsedMs(started));
                result.setStatus("COMPLETED");
                return result;
            }

            if (isPlanExecute(command)) {
                AgentRunResult result = multiAgentOrchestrator.planAndExecute(context, persistedSink);
                if (AgentRunStatus.WAITING_APPROVAL.name().equals(result.getStatus())) {
                    return result;
                }
                saveAssistantMessage(conversation.getId(), result.getAnswer());
                MemoryCaptureResult memoryCaptureResult = captureMemory(command, conversation.getId(), userMessage.getId(), result.getAnswer());
                lifecycleService.completeRun(run.getId(), result);
                emit(persistedSink, run.getId(), traceId, RuntimeEventType.RUN_FINISHED, "运行完成",
                        "ExecutorAgent 已完成计划执行", Map.of(
                                "status", AgentRunStatus.COMPLETED.name(),
                                "inputTokens", result.getInputTokens(),
                                "outputTokens", result.getOutputTokens(),
                                "modelCallCount", result.getModelCallCount(),
                                "memoryCaptured", memoryCaptureResult.captured(),
                                "memoryActivated", memoryCaptureResult.activated(),
                                "memoryPending", memoryCaptureResult.pending(),
                                "memoryConflicts", memoryCaptureResult.conflicts(),
                                "conversationId", conversation.getId(),
                                "runMode", "PLAN_EXECUTE"
                        ), elapsedMs(started));
                result.setStatus("COMPLETED");
                return result;
            }

            AgentRunResult result = agentScopeRuntimeAdapter.execute(context, persistedSink);
            if (AgentRunStatus.WAITING_APPROVAL.name().equals(result.getStatus())) {
                return result;
            }
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

    public AgentRunResult resumeApprovalStreaming(AgentApprovalCommand command, RuntimeEventSink clientSink) {
        validateApprovalCommand(command);
        long started = System.nanoTime();

        ApprovalRequestEntity approval = toolApprovalService.decide(command);
        AgentRunEntity run = agentRunRepository.findById(approval.getRunId())
                .orElseThrow(() -> new BusinessException(404, "Agent Run 不存在：" + approval.getRunId()));
        if (AgentRunStatus.from(run.getStatus()).isTerminal()) {
            throw new BusinessException(409, "Agent Run 已经结束，不能继续审批恢复：" + run.getStatus());
        }

        RuntimeEventSink persistedSink = wrapLifecycleSink(run.getId(), run.getTraceId(), clientSink, started);
        AgentRunEntity running = lifecycleService.resumeFromApproval(run.getId());
        emit(persistedSink, run.getId(), run.getTraceId(), RuntimeEventType.RUN_STATUS_CHANGED,
                "状态变更：继续运行", "用户审批结果已提交，Agent Run 继续执行",
                Map.of("status", running.getStatus(), "approvalId", approval.getId()), elapsedMs(started));

        try {
            AgentRunCommand resumeCommand = buildApprovalResumeCommand(command, run);
            if (toolApprovalService.isPlatformToolApproval(approval)) {
                return resumePlatformToolApproval(command, approval, run, resumeCommand, persistedSink, started);
            }
            ToolApprovalService.ToolApprovalPayload payload = toolApprovalService.loadPayload(approval);
            RuntimeContext context = contextBuilder.build(
                    resumeCommand,
                    run.getConversationId(),
                    run.getUserMessageId(),
                    run.getId(),
                    run.getTraceId()
            );
            context.setRuntimeEventSink(persistedSink);
            context.setRunStartedNanos(started);

            AgentRunResult result = agentScopeRuntimeAdapter.resumeAfterApproval(
                    context,
                    payload,
                    Boolean.TRUE.equals(command.getApproved()),
                    persistedSink
            );
            if (AgentRunStatus.WAITING_APPROVAL.name().equals(result.getStatus())) {
                return result;
            }

            saveAssistantMessage(run.getConversationId(), result.getAnswer());
            MemoryCaptureResult memoryCaptureResult = captureMemory(
                    resumeCommand,
                    run.getConversationId(),
                    run.getUserMessageId(),
                    result.getAnswer()
            );
            lifecycleService.completeRun(run.getId(), result);
            emit(persistedSink, run.getId(), run.getTraceId(), RuntimeEventType.RUN_FINISHED, "运行完成",
                    "智能体运行已完成", Map.of(
                            "status", AgentRunStatus.COMPLETED.name(),
                            "inputTokens", result.getInputTokens(),
                            "outputTokens", result.getOutputTokens(),
                            "modelCallCount", result.getModelCallCount(),
                            "memoryCaptured", memoryCaptureResult.captured(),
                            "memoryActivated", memoryCaptureResult.activated(),
                            "memoryPending", memoryCaptureResult.pending(),
                            "memoryConflicts", memoryCaptureResult.conflicts(),
                            "conversationId", run.getConversationId()
                    ), elapsedMs(started));
            result.setStatus(AgentRunStatus.COMPLETED.name());
            return result;
        } catch (Exception e) {
            AgentRunStatus errorStatus = isTimeout(e) ? AgentRunStatus.TIMEOUT : AgentRunStatus.FAILED;
            if (errorStatus == AgentRunStatus.TIMEOUT) {
                lifecycleService.timeoutRun(run.getId(), e.getMessage());
            } else {
                lifecycleService.failRun(run.getId(), e.getMessage());
            }
            emit(persistedSink, run.getId(), run.getTraceId(), RuntimeEventType.RUN_ERROR, "运行异常",
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

    private void normalizeRunMode(AgentRunCommand command) {
        String message = command.getMessage() != null ? command.getMessage().trim() : "";
        if (message.startsWith("/plan")) {
            command.setRunMode("PLAN_ONLY");
            String task = message.substring("/plan".length()).trim();
            if (!StringUtils.hasText(task)) {
                throw new BusinessException(400, "/plan 后面需要写清楚要规划的任务");
            }
            command.setMessage(task);
            return;
        }
        if (!StringUtils.hasText(command.getRunMode())) {
            command.setRunMode("SINGLE_AGENT");
        }
    }

    private boolean isPlanOnly(AgentRunCommand command) {
        return "PLAN_ONLY".equalsIgnoreCase(command.getRunMode());
    }

    private boolean isPlanExecute(AgentRunCommand command) {
        return "PLAN_EXECUTE".equalsIgnoreCase(command.getRunMode());
    }

    private void validateApprovalCommand(AgentApprovalCommand command) {
        if (command == null) {
            throw new BusinessException(400, "缺少审批命令");
        }
        if (command.getApprovalRequestId() == null) {
            throw new BusinessException(400, "审批请求 ID 不能为空");
        }
        if (command.getApproved() == null) {
            throw new BusinessException(400, "审批结果 approved 不能为空");
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
            RuntimeEvent enriched = enrichLifecycleEvent(event);
            traceService.recordAndForward(enriched, clientSink);
            handleLifecycleEvent(runId, traceId, enriched, clientSink, startedNanos);
        };
    }

    private RuntimeEvent enrichLifecycleEvent(RuntimeEvent event) {
        if (event == null || event.getType() != RuntimeEventType.CONFIRMATION_REQUIRED) {
            return event;
        }
        Map<String, Object> metadata = new java.util.LinkedHashMap<>(
                event.getMetadata() != null ? event.getMetadata() : Map.of()
        );
        if (metadata.containsKey("approvalId") || metadata.containsKey("approvalRequests")) {
            event.setMetadata(metadata);
            return event;
        }
        var approvals = toolApprovalService.createPendingApprovals(event);
        metadata.put("approvalRequests", approvals);
        if (!approvals.isEmpty()) {
            metadata.put("approvalId", approvals.get(0).get("approvalId"));
        }
        event.setMetadata(metadata);
        return event;
    }

    private AgentRunResult resumePlatformToolApproval(AgentApprovalCommand approvalCommand,
                                                      ApprovalRequestEntity approval,
                                                      AgentRunEntity run,
                                                      AgentRunCommand resumeCommand,
                                                      RuntimeEventSink sink,
                                                      long started) {
        ToolApprovalService.PlatformToolApprovalPayload payload = toolApprovalService.loadPlatformToolPayload(approval);
        emit(sink, run.getId(), run.getTraceId(), RuntimeEventType.CONFIRMATION_RESULT, "用户确认结果",
                Boolean.TRUE.equals(approvalCommand.getApproved())
                        ? "用户已批准平台工具调用"
                        : "用户已拒绝平台工具调用",
                Map.of(
                        "approvalId", approval.getId(),
                        "toolCallId", safe(payload.toolCallId()),
                        "toolName", safe(payload.toolName()),
                        "approved", Boolean.TRUE.equals(approvalCommand.getApproved())
                ), elapsedMs(started));

        if (!Boolean.TRUE.equals(approvalCommand.getApproved())) {
            String answer = "用户已拒绝执行工具 " + safe(payload.toolName()) + "，本次高风险操作已停止。";
            emitAssistantAnswer(sink, run, answer, started);
            saveAssistantMessage(run.getConversationId(), answer);
            AgentRunResult result = basePlatformResult(run, answer);
            lifecycleService.completeRun(run.getId(), result);
            emit(sink, run.getId(), run.getTraceId(), RuntimeEventType.RUN_FINISHED, "运行完成",
                    "平台工具调用已被用户拒绝，运行结束",
                    Map.of("status", AgentRunStatus.COMPLETED.name(), "conversationId", run.getConversationId()),
                    elapsedMs(started));
            return result;
        }

        RuntimeContext context = contextBuilder.build(
                resumeCommand,
                run.getConversationId(),
                run.getUserMessageId(),
                run.getId(),
                run.getTraceId()
        );
        context.setRuntimeEventSink(sink);
        context.setRunStartedNanos(started);

        String toolName = safe(payload.toolName());
        String toolCallId = safe(payload.toolCallId());
        emit(sink, run.getId(), run.getTraceId(), RuntimeEventType.TOOL_CALL_STARTED, "开始准备工具调用",
                null, toolCallMetadata(toolCallId, toolName, payload.input()), elapsedMs(started));
        emit(sink, run.getId(), run.getTraceId(), RuntimeEventType.TOOL_RESULT_STARTED, "开始返回工具结果",
                null, toolMetadata(toolCallId, toolName), elapsedMs(started));

        PlatformToolExecutionOutcome outcome;
        if (isCommandTool(toolName)) {
            outcome = executeApprovedCommand(context, payload.input());
        } else {
            String text = "平台工具审批已通过，但暂未实现该工具的直接恢复执行：" + toolName;
            outcome = new PlatformToolExecutionOutcome(text, text, "ERROR");
        }

        emit(sink, run.getId(), run.getTraceId(), RuntimeEventType.TOOL_RESULT_DELTA, "工具结果文本增量",
                outcome.toolResult(), toolMetadata(toolCallId, toolName), elapsedMs(started));
        emit(sink, run.getId(), run.getTraceId(), RuntimeEventType.TOOL_RESULT_FINISHED, "工具结果返回完成",
                null, Map.of("toolCallId", toolCallId, "callId", toolCallId, "state", outcome.toolState()),
                elapsedMs(started));

        emitAssistantAnswer(sink, run, outcome.finalAnswer(), started);
        saveAssistantMessage(run.getConversationId(), outcome.finalAnswer());
        AgentRunResult result = basePlatformResult(run, outcome.finalAnswer());
        lifecycleService.completeRun(run.getId(), result);
        emit(sink, run.getId(), run.getTraceId(), RuntimeEventType.RUN_FINISHED, "运行完成",
                "平台工具调用已完成", Map.of(
                        "status", AgentRunStatus.COMPLETED.name(),
                        "conversationId", run.getConversationId()
                ), elapsedMs(started));
        return result;
    }

    private PlatformToolExecutionOutcome executeApprovedCommand(RuntimeContext context, Map<String, Object> input) {
        String command = stringValue(input.get("command"));
        String description = stringValue(input.get("description"));
        Integer timeoutSeconds = integerValue(input.get("timeoutSeconds"));
        String workingDirectory = stringValue(input.get("workingDirectory"));
        CommandExecutionResult result = commandSandboxService.execute(
                context.getWorkspace().getRootPath(),
                workingDirectory,
                command,
                timeoutSeconds
        );
        String toolResult = formatCommandResult(result, description);
        return new PlatformToolExecutionOutcome(
                toolResult,
                formatCommandFinalAnswer(result),
                commandToolState(result)
        );
    }

    private String formatCommandResult(CommandExecutionResult result, String description) {
        StringBuilder builder = new StringBuilder();
        builder.append("命令：").append(safe(result.command())).append("\n");
        if (StringUtils.hasText(description)) {
            builder.append("目的：").append(description.trim()).append("\n");
        }
        builder.append("工作目录：").append(safe(result.workingDirectory())).append("\n");
        if (!result.allowed()) {
            builder.append("执行状态：REJECTED\n")
                    .append("拒绝原因：").append(safe(result.rejectReason())).append("\n");
            return builder.toString();
        }
        builder.append("执行状态：").append(result.timedOut() ? "TIMEOUT" : "COMPLETED").append("\n")
                .append("退出码：").append(result.exitCode() == null ? "N/A" : result.exitCode()).append("\n")
                .append("耗时：").append(result.durationMs()).append("ms\n")
                .append("stdout 截断：").append(result.stdoutTruncated() ? "是" : "否").append("\n")
                .append("stderr 截断：").append(result.stderrTruncated() ? "是" : "否").append("\n\n")
                .append("STDOUT:\n")
                .append(StringUtils.hasText(result.stdout()) ? result.stdout() : "(empty)")
                .append("\n\nSTDERR:\n")
                .append(StringUtils.hasText(result.stderr()) ? result.stderr() : "(empty)");
        return builder.toString();
    }

    private String formatCommandFinalAnswer(CommandExecutionResult result) {
        if (!result.allowed()) {
            return "已收到你的批准，但命令没有执行。\n\n"
                    + "原因：命令被沙箱拒绝：" + safe(result.rejectReason()) + "\n\n"
                    + "这不是还在等待审批，而是审批通过后的第二层命令沙箱拦截。删除类操作后续应该走受控删除工具，不应该通过 Bash 执行。";
        }
        if (result.timedOut()) {
            return "命令已开始执行，但超过超时时间后被平台终止。\n\n"
                    + "命令：" + safe(result.command());
        }
        if (result.exitCode() != null && result.exitCode() != 0) {
            return "命令已执行完成，但退出码不是 0。\n\n"
                    + "命令：" + safe(result.command()) + "\n"
                    + "退出码：" + result.exitCode();
        }
        return "命令已执行完成。\n\n"
                + "命令：" + safe(result.command()) + "\n"
                + "退出码：" + (result.exitCode() == null ? "N/A" : result.exitCode());
    }

    private String commandToolState(CommandExecutionResult result) {
        if (!result.allowed() || result.timedOut()) {
            return "ERROR";
        }
        if (result.exitCode() != null && result.exitCode() != 0) {
            return "ERROR";
        }
        return "SUCCESS";
    }

    private void emitAssistantAnswer(RuntimeEventSink sink, AgentRunEntity run, String answer, long started) {
        emit(sink, run.getId(), run.getTraceId(), RuntimeEventType.ANSWER_STARTED, "开始生成回答",
                null, Map.of("source", "PLATFORM_TOOL_APPROVAL"), elapsedMs(started));
        emit(sink, run.getId(), run.getTraceId(), RuntimeEventType.ANSWER_DELTA, "回答增量",
                answer, Map.of("source", "PLATFORM_TOOL_APPROVAL"), elapsedMs(started));
        emit(sink, run.getId(), run.getTraceId(), RuntimeEventType.ANSWER_FINISHED, "回答生成完成",
                null, Map.of("source", "PLATFORM_TOOL_APPROVAL"), elapsedMs(started));
    }

    private AgentRunResult basePlatformResult(AgentRunEntity run, String answer) {
        AgentRunResult result = new AgentRunResult();
        result.setRunId(run.getId());
        result.setConversationId(run.getConversationId());
        result.setTraceId(run.getTraceId());
        result.setAnswer(answer);
        result.setInputTokens(0);
        result.setOutputTokens(estimateTokens(answer));
        result.setModelCallCount(0);
        result.setStatus(AgentRunStatus.COMPLETED.name());
        return result;
    }

    private Map<String, Object> toolMetadata(String toolCallId, String toolName) {
        return Map.of(
                "toolCallId", toolCallId,
                "callId", toolCallId,
                "tool", toolName,
                "toolName", toolName
        );
    }

    private Map<String, Object> toolCallMetadata(String toolCallId, String toolName, Map<String, Object> args) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>(toolMetadata(toolCallId, toolName));
        metadata.put("args", args != null ? args : Map.of());
        return metadata;
    }

    private boolean isCommandTool(String toolName) {
        return "Bash".equalsIgnoreCase(toolName)
                || "Shell".equalsIgnoreCase(toolName)
                || "run_command".equalsIgnoreCase(toolName)
                || "runCommand".equalsIgnoreCase(toolName);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record PlatformToolExecutionOutcome(String toolResult, String finalAnswer, String toolState) {
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

    private AgentRunCommand buildApprovalResumeCommand(AgentApprovalCommand approvalCommand, AgentRunEntity run) {
        AgentRunCommand command = new AgentRunCommand();
        command.setWorkspaceId(run.getWorkspaceId());
        command.setAgentId(run.getAgentId());
        command.setConversationId(run.getConversationId());
        command.setUserId(StringUtils.hasText(approvalCommand.getUserId()) ? approvalCommand.getUserId() : "default");
        command.setMessage(loadOriginalUserMessage(run.getUserMessageId()));
        command.setModelBaseUrl(approvalCommand.getModelBaseUrl());
        command.setModelName(approvalCommand.getModelName());
        command.setApiKey(approvalCommand.getApiKey());
        command.setTimeoutSeconds(approvalCommand.getTimeoutSeconds());
        command.setTraceThinkingContent(approvalCommand.getTraceThinkingContent());
        return command;
    }

    private String loadOriginalUserMessage(Long userMessageId) {
        if (userMessageId == null) {
            return "用户已处理工具审批";
        }
        return conversationMessageRepository.findById(userMessageId)
                .map(ConversationMessageEntity::getContent)
                .filter(StringUtils::hasText)
                .orElse("用户已处理工具审批");
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
