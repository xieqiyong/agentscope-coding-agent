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
 * 中文注释：当前只做 workspace 级校验，工具审批已经关闭，这里统一放行。
 */
@Service
public class RuntimeToolGuard {

    public static final String APPROVAL_MODE_PLATFORM_TOOL_GUARD = "PLATFORM_TOOL_GUARD";

    public ToolGuardDecision beforeToolExecution(RuntimeContext context,
                                                 String toolName,
                                                 Map<String, Object> input,
                                                 String summary) {
        return ToolGuardDecision.allow();
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
