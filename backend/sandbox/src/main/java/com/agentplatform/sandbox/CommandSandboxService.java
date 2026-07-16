package com.agentplatform.sandbox;

import com.agentplatform.common.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 命令级沙箱服务。
 * 中文注释：这里只保证命令工作目录在 workspace 内，同时保留超时和输出截断控制。
 */
@Service
public class CommandSandboxService {

    @Resource
    private SandboxPathResolver sandboxPathResolver;

    @Resource
    private CommandSandboxProperties properties;

    public CommandExecutionResult execute(String workspaceRootPath,
                                          String requestedWorkingDirectory,
                                          String command,
                                          Integer timeoutSeconds) {
        ResolvedWorkspacePath workingDirectory = resolveWorkingDirectory(workspaceRootPath, requestedWorkingDirectory);
        String normalizedCommand = normalizeCommand(command);
        String rejectReason = validateCommand(normalizedCommand);
        if (rejectReason != null) {
            return CommandExecutionResult.rejected(normalizedCommand, displayWorkingDirectory(workingDirectory), rejectReason);
        }

        int timeout = normalizeTimeout(timeoutSeconds);
        long startedNanos = System.nanoTime();
        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(shellCommand(normalizedCommand));
            processBuilder.directory(workingDirectory.absolutePath().toFile());
            // 中文注释：第一版不继承额外环境治理，先保留系统环境；后续治理增强再做白名单环境。
            process = processBuilder.start();

            OutputCollector stdoutCollector = new OutputCollector(process.getInputStream(), properties.getMaxOutputChars());
            OutputCollector stderrCollector = new OutputCollector(process.getErrorStream(), properties.getMaxOutputChars());
            CompletableFuture<OutputSnapshot> stdoutFuture = CompletableFuture.supplyAsync(stdoutCollector::read);
            CompletableFuture<OutputSnapshot> stderrFuture = CompletableFuture.supplyAsync(stderrCollector::read);

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }

            Integer exitCode = finished ? process.exitValue() : null;
            OutputSnapshot stdout = stdoutFuture.get(2, TimeUnit.SECONDS);
            OutputSnapshot stderr = stderrFuture.get(2, TimeUnit.SECONDS);
            long durationMs = elapsedMs(startedNanos);
            return new CommandExecutionResult(true, true, null, normalizedCommand,
                    displayWorkingDirectory(workingDirectory), exitCode, !finished, durationMs,
                    stdout.text(), stderr.text(), stdout.truncated(), stderr.truncated());
        } catch (Exception e) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            throw new BusinessException(500, "命令执行失败：" + e.getMessage());
        }
    }

    private ResolvedWorkspacePath resolveWorkingDirectory(String workspaceRootPath, String requestedWorkingDirectory) {
        ResolvedWorkspacePath resolved = sandboxPathResolver.resolveExistingPath(workspaceRootPath, requestedWorkingDirectory);
        if (!Files.isDirectory(resolved.absolutePath())) {
            throw new BusinessException(400, "命令工作目录必须是 workspace 内的目录");
        }
        return resolved;
    }

    private String normalizeCommand(String command) {
        if (!StringUtils.hasText(command)) {
            return "";
        }
        return command.trim().replaceAll("\\s+", " ");
    }

    private String validateCommand(String command) {
        if (!properties.isEnabled()) {
            return "命令工具未启用";
        }
        if (!StringUtils.hasText(command)) {
            return "命令不能为空";
        }
        if (command.contains("\n") || command.contains("\r") || command.indexOf('\0') >= 0) {
            return "命令不能包含换行或空字符";
        }
        return null;
    }

    private int normalizeTimeout(Integer timeoutSeconds) {
        int fallback = Math.max(1, properties.getDefaultTimeoutSeconds());
        int max = Math.max(fallback, properties.getMaxTimeoutSeconds());
        int requested = timeoutSeconds != null && timeoutSeconds > 0 ? timeoutSeconds : fallback;
        return Math.min(requested, max);
    }

    private List<String> shellCommand(String command) {
        if (isWindows()) {
            return List.of("cmd.exe", "/d", "/s", "/c", command);
        }
        return List.of("bash", "-lc", command);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String displayWorkingDirectory(ResolvedWorkspacePath workingDirectory) {
        return StringUtils.hasText(workingDirectory.relativePath())
                ? workingDirectory.relativePath()
                : ".";
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private long elapsedMs(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private record OutputSnapshot(String text, boolean truncated) {
    }

    private static class OutputCollector {

        private final InputStream inputStream;
        private final int maxChars;

        private OutputCollector(InputStream inputStream, int maxChars) {
            this.inputStream = inputStream;
            this.maxChars = Math.max(1, maxChars);
        }

        private OutputSnapshot read() {
            StringBuilder builder = new StringBuilder();
            boolean truncated = false;
            Charset charset = StandardCharsets.UTF_8;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int remaining = maxChars - builder.length();
                    if (remaining <= 0) {
                        truncated = true;
                        continue;
                    }
                    String text = line + System.lineSeparator();
                    if (text.length() > remaining) {
                        builder.append(text, 0, remaining);
                        truncated = true;
                    } else {
                        builder.append(text);
                    }
                }
            } catch (IOException e) {
                builder.append("[读取输出失败] ").append(e.getMessage());
            }
            return new OutputSnapshot(builder.toString(), truncated);
        }
    }
}
