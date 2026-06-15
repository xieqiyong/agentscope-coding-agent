package com.agentplatform.runtime.tool;

import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.runtime.service.RuntimeToolGuard;
import com.agentplatform.sandbox.CommandExecutionResult;
import com.agentplatform.sandbox.CommandSandboxService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.ToolSuspendException;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 暴露给 AgentScope 的命令工具。
 * 中文注释：工具本身不信任模型输入，真正执行前必须再次经过 CommandSandboxService。
 */
public class CodingAgentCommandTools {

    private final RuntimeContext context;
    private final CommandSandboxService commandSandboxService;
    private final RuntimeToolGuard runtimeToolGuard;

    public CodingAgentCommandTools(RuntimeContext context,
                                   CommandSandboxService commandSandboxService,
                                   RuntimeToolGuard runtimeToolGuard) {
        this.context = context;
        this.commandSandboxService = commandSandboxService;
        this.runtimeToolGuard = runtimeToolGuard;
    }

    @Tool(name = "Bash", description = "在当前工作区内执行非交互命令。仅允许沙箱 allowlist 内的构建、测试和只读 git 命令；高风险命令会先请求用户确认。", readOnly = false)
    public String Bash(
            @ToolParam(name = "command", description = "要执行的单行命令，例如 npm test、mvn test、git status。") String command,
            @ToolParam(name = "description", required = false, description = "执行这个命令的简短目的说明。") String description,
            @ToolParam(name = "timeoutSeconds", required = false, description = "超时时间，单位秒，会被沙箱最大值限制。") Integer timeoutSeconds,
            @ToolParam(name = "workingDirectory", required = false, description = "工作区相对目录，默认工作区根目录。") String workingDirectory) {
        return execute(command, description, timeoutSeconds, workingDirectory);
    }

    @Tool(name = "Shell", description = "Bash 的别名。用于在当前工作区执行受沙箱限制的非交互命令。", readOnly = false)
    public String Shell(
            @ToolParam(name = "command", description = "要执行的单行命令。") String command,
            @ToolParam(name = "description", required = false, description = "执行这个命令的简短目的说明。") String description,
            @ToolParam(name = "timeoutSeconds", required = false, description = "超时时间，单位秒。") Integer timeoutSeconds,
            @ToolParam(name = "workingDirectory", required = false, description = "工作区相对目录，默认工作区根目录。") String workingDirectory) {
        return execute(command, description, timeoutSeconds, workingDirectory);
    }

    @Tool(name = "run_command", description = "Bash 的兼容别名。用于执行受沙箱限制的工作区命令。", readOnly = false)
    public String runCommand(
            @ToolParam(name = "command", description = "要执行的单行命令。") String command,
            @ToolParam(name = "description", required = false, description = "执行这个命令的简短目的说明。") String description,
            @ToolParam(name = "timeoutSeconds", required = false, description = "超时时间，单位秒。") Integer timeoutSeconds,
            @ToolParam(name = "workingDirectory", required = false, description = "工作区相对目录，默认工作区根目录。") String workingDirectory) {
        return execute(command, description, timeoutSeconds, workingDirectory);
    }

    private String execute(String command, String description, Integer timeoutSeconds, String workingDirectory) {
        RuntimeToolGuard.ToolGuardDecision decision = runtimeToolGuard.beforeToolExecution(
                context,
                "Bash",
                buildInput(command, description, timeoutSeconds, workingDirectory),
                StringUtils.hasText(description) ? description : command
        );
        if (decision.approvalRequired()) {
            // 中文注释：平台级审批是一次真正的 interrupt，不能把“等待审批”当普通工具结果继续喂给模型。
            throw new ToolSuspendException(decision.message());
        }

        CommandExecutionResult result = commandSandboxService.execute(
                context.getWorkspace().getRootPath(),
                workingDirectory,
                command,
                timeoutSeconds
        );
        return formatResult(result, description);
    }

    private Map<String, Object> buildInput(String command, String description,
                                           Integer timeoutSeconds, String workingDirectory) {
        Map<String, Object> input = new java.util.LinkedHashMap<>();
        input.put("command", command);
        if (StringUtils.hasText(description)) {
            input.put("description", description);
        }
        if (timeoutSeconds != null) {
            input.put("timeoutSeconds", timeoutSeconds);
        }
        if (StringUtils.hasText(workingDirectory)) {
            input.put("workingDirectory", workingDirectory);
        }
        return input;
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
