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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 命令级沙箱服务。
 * 中文注释：这里负责命令执行前的工作区、命令形态、允许列表、超时和输出截断控制。
 */
@Service
public class CommandSandboxService {

    private static final List<String> FORBIDDEN_CONTROL_OPERATORS = List.of(
            "&&", "||", ";", "|", "`", "$(", ">", "<"
    );

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

        String lower = command.toLowerCase(Locale.ROOT);
        for (String operator : FORBIDDEN_CONTROL_OPERATORS) {
            if (lower.contains(operator)) {
                return "命令包含暂未开放的 shell 控制符：" + operator;
            }
        }
        for (String fragment : safeList(properties.getDeniedFragments())) {
            if (StringUtils.hasText(fragment) && lower.contains(fragment.trim().toLowerCase(Locale.ROOT))) {
                return "命令命中危险片段：" + fragment;
            }
        }
        List<String> tokens = splitCommand(command);
        if (tokens.isEmpty()) {
            return "命令不能为空";
        }
        String pathArgumentRejectReason = validatePathArguments(tokens);
        if (pathArgumentRejectReason != null) {
            return pathArgumentRejectReason;
        }
        String executable = normalizeExecutable(tokens.get(0));
        for (String denied : safeList(properties.getDeniedInteractiveCommands())) {
            if (executable.equalsIgnoreCase(normalizeExecutable(denied))) {
                return "不允许启动交互式命令：" + executable;
            }
        }
        // todo 测试阶段注释
//        if (!matchesAllowlist(tokens)) {
//            return "命令不在允许列表内";
//        }
        return null;
    }

    private String validatePathArguments(List<String> tokens) {
        for (String token : tokens) {
            String normalized = token.replace('\\', '/');
            if (normalized.equals("..") || normalized.startsWith("../") || normalized.contains("/../")) {
                return "命令参数不能包含父目录跳转：" + token;
            }
            if (normalized.startsWith("~")) {
                return "命令参数不能使用用户主目录路径：" + token;
            }
            if (normalized.matches("^[A-Za-z]:/.*")) {
                return "命令参数不能使用绝对路径：" + token;
            }
            if (normalized.startsWith("/") && !normalized.startsWith("./")) {
                return "命令参数不能使用绝对路径：" + token;
            }
        }
        return null;
    }

    private boolean matchesAllowlist(List<String> commandTokens) {
        for (String prefix : safeList(properties.getAllowlistedPrefixes())) {
            List<String> prefixTokens = splitCommand(prefix);
            if (prefixTokens.isEmpty() || commandTokens.size() < prefixTokens.size()) {
                continue;
            }
            boolean matched = true;
            for (int i = 0; i < prefixTokens.size(); i++) {
                if (!commandTokens.get(i).equalsIgnoreCase(prefixTokens.get(i))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitCommand(String command) {
        List<String> tokens = new ArrayList<>();
        if (!StringUtils.hasText(command)) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (Character.isWhitespace(ch) && !inSingleQuote && !inDoubleQuote) {
                addToken(tokens, current);
                continue;
            }
            current.append(ch);
        }
        addToken(tokens, current);
        return tokens;
    }

    private void addToken(List<String> tokens, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        tokens.add(current.toString());
        current.setLength(0);
    }

    private String normalizeExecutable(String executable) {
        if (executable == null) {
            return "";
        }
        String normalized = executable.trim().replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(slashIndex + 1);
        }
        return normalized;
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
