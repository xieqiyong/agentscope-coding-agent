package com.agentplatform.sandbox;

/**
 * 命令执行结果。
 * 中文注释：沙箱只返回结构化结果，不在这里决定前端如何展示。
 */
public record CommandExecutionResult(
        boolean executed,
        boolean allowed,
        String rejectReason,
        String command,
        String workingDirectory,
        Integer exitCode,
        boolean timedOut,
        long durationMs,
        String stdout,
        String stderr,
        boolean stdoutTruncated,
        boolean stderrTruncated
) {

    public static CommandExecutionResult rejected(String command, String workingDirectory, String rejectReason) {
        return new CommandExecutionResult(false, false, rejectReason, command, workingDirectory,
                null, false, 0, "", "", false, false);
    }
}
