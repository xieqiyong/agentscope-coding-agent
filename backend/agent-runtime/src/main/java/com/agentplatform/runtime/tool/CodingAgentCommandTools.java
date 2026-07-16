package com.agentplatform.runtime.tool;

import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.sandbox.CommandExecutionResult;
import com.agentplatform.sandbox.CommandSandboxService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.util.StringUtils;

/**
 * 暴露给 AgentScope 的命令工具。
 * 中文注释：工具本身不信任模型输入，真正执行前必须再次经过 CommandSandboxService。
 */
public class CodingAgentCommandTools {

    private final RuntimeContext context;
    private final CommandSandboxService commandSandboxService;

    public CodingAgentCommandTools(RuntimeContext context,
                                   CommandSandboxService commandSandboxService) {
        this.context = context;
        this.commandSandboxService = commandSandboxService;
    }

    @Tool(name = "Bash", description = "在当前工作区内执行命令；平台只校验工作目录必须位于当前 workspace 内。", readOnly = false)
    public String Bash(
            @ToolParam(name = "command", description = "要执行的单行命令，例如 npm test、mvn test、git status。") String command,
            @ToolParam(name = "description", required = false, description = "执行这个命令的简短目的说明。") String description,
            @ToolParam(name = "timeoutSeconds", required = false, description = "超时时间，单位秒，会被沙箱最大值限制。") Integer timeoutSeconds,
            @ToolParam(name = "workingDirectory", required = false, description = "工作区相对目录，默认工作区根目录。") String workingDirectory) {
        return execute(command, description, timeoutSeconds, workingDirectory);
    }

    public String Shell(
            @ToolParam(name = "command", description = "要执行的单行命令。") String command,
            @ToolParam(name = "description", required = false, description = "执行这个命令的简短目的说明。") String description,
            @ToolParam(name = "timeoutSeconds", required = false, description = "超时时间，单位秒。") Integer timeoutSeconds,
            @ToolParam(name = "workingDirectory", required = false, description = "工作区相对目录，默认工作区根目录。") String workingDirectory) {
        return execute(command, description, timeoutSeconds, workingDirectory);
    }

    public String runCommand(
            @ToolParam(name = "command", description = "要执行的单行命令。") String command,
            @ToolParam(name = "description", required = false, description = "执行这个命令的简短目的说明。") String description,
            @ToolParam(name = "timeoutSeconds", required = false, description = "超时时间，单位秒。") Integer timeoutSeconds,
            @ToolParam(name = "workingDirectory", required = false, description = "工作区相对目录，默认工作区根目录。") String workingDirectory) {
        return execute(command, description, timeoutSeconds, workingDirectory);
    }

    private String execute(String command, String description, Integer timeoutSeconds, String workingDirectory) {
        CommandExecutionResult result = commandSandboxService.execute(
                context.getWorkspace().getRootPath(),
                workingDirectory,
                command,
                timeoutSeconds
        );
        return formatResult(result, description);
    }

    private String formatResult(CommandExecutionResult result, String description) {
        StringBuilder builder = new StringBuilder();
        builder.append("命令：").append(nullToEmpty(result.command())).append("\n");
        if (StringUtils.hasText(description)) {
            builder.append("目的：").append(description.trim()).append("\n");
        }
        builder.append("工作目录：").append(nullToEmpty(result.workingDirectory())).append("\n");

        if (!result.allowed()) {
            builder.append("执行状态：REJECTED\n")
                    .append("拒绝原因：").append(nullToEmpty(result.rejectReason())).append("\n");
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

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
