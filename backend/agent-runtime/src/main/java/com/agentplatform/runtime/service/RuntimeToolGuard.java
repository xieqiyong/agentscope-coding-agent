package com.agentplatform.runtime.service;

import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 平台级工具治理入口。
 * 中文注释：这里不依赖 AgentScope PermissionEngine，而是在工具真正执行前由平台自己决定 ALLOW / ASK / DENY。
 */
@Service
public class RuntimeToolGuard {

    public static final String APPROVAL_MODE_PLATFORM_TOOL_GUARD = "PLATFORM_TOOL_GUARD";

    public ToolGuardDecision beforeToolExecution(RuntimeContext context,
                                                 String toolName,
                                                 Map<String, Object> input,
                                                 String summary) {
        if (!requiresApproval(toolName)) {
            return ToolGuardDecision.allow();
        }

        String toolCallId = "platform-tool-" + UUID.randomUUID();
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", toolCallId);
        toolCall.put("name", toolName);
        toolCall.put("input", input != null ? input : Map.of());
        toolCall.put("content", StringUtils.hasText(summary) ? summary : "平台工具执行确认");
        toolCall.put("metadata", Map.of("approvalMode", APPROVAL_MODE_PLATFORM_TOOL_GUARD));
        toolCall.put("state", "ASKING");
        toolCall.put("riskLevel", classifyRisk(toolName));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceEvent", "RuntimeToolGuard");
        metadata.put("requestType", "TOOL_PERMISSION");
        metadata.put("approvalMode", APPROVAL_MODE_PLATFORM_TOOL_GUARD);
        metadata.put("replyId", "platform-tool-guard-" + toolCallId);
        metadata.put("toolCalls", List.of(toolCall));
        metadata.put("riskLevel", classifyRisk(toolName));
        metadata.put("reason", "平台 ToolGuard 要求用户确认后再执行高风险工具");

        context.setPlatformApprovalRequired(true);
        context.getRuntimeEventSink().emit(RuntimeEvent.of(
                context.getRunId(),
                context.getTraceId(),
                RuntimeEventType.CONFIRMATION_REQUIRED,
                "需要用户确认",
                "平台请求确认执行工具：" + toolName,
                metadata,
                elapsedMs(context)
        ));
        return ToolGuardDecision.ask(toolCallId, "平台已暂停工具执行，等待用户确认：" + toolName);
    }

    public String classifyRisk(String toolName) {
        if (isCommandTool(toolName)) {
            return "CRITICAL";
        }
        if ("apply_patch".equals(toolName) || "Edit".equals(toolName)
                || "write_file".equals(toolName) || "Write".equals(toolName)) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private boolean requiresApproval(String toolName) {
        return isCommandTool(toolName);
    }

    private boolean isCommandTool(String toolName) {
        return "Bash".equalsIgnoreCase(toolName)
                || "Shell".equalsIgnoreCase(toolName)
                || "run_command".equalsIgnoreCase(toolName)
                || "runCommand".equalsIgnoreCase(toolName);
    }

    private long elapsedMs(RuntimeContext context) {
        long started = context.getRunStartedNanos();
        if (started <= 0) {
            return 0;
        }
        return (System.nanoTime() - started) / 1_000_000;
    }

    public record ToolGuardDecision(boolean allowed, boolean approvalRequired, String toolCallId, String message) {

        public static ToolGuardDecision allow() {
            return new ToolGuardDecision(true, false, null, "");
        }

        public static ToolGuardDecision ask(String toolCallId, String message) {
            return new ToolGuardDecision(false, true, toolCallId, message);
        }
    }
}
