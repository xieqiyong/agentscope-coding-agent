package com.agentplatform.sandbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令级沙箱配置。
 * 中文注释：第一版采用允许前缀策略，默认只开放常见构建、测试和只读 git 命令。
 */
@Component
@ConfigurationProperties(prefix = "agent.sandbox.command")
public class CommandSandboxProperties {

    /**
     * 是否允许 Agent 执行命令工具。
     */
    private boolean enabled = true;

    /**
     * 默认超时时间，单位秒。
     */
    private int defaultTimeoutSeconds = 60;

    /**
     * 最大超时时间，单位秒。
     */
    private int maxTimeoutSeconds = 300;

    /**
     * stdout/stderr 各自最多保留的字符数。
     */
    private int maxOutputChars = 20_000;

    /**
     * 允许执行的命令前缀。
     */
    private List<String> allowlistedPrefixes = new ArrayList<>(List.of(
            "mvn test",
            "mvn -q test",
            "mvn -q -DskipTests compile",
            "mvn -q -DskipTests package",
            "mvnw test",
            "mvnw -q test",
            "mvnw -q -DskipTests compile",
            "mvnw -q -DskipTests package",
            "./mvnw test",
            "./mvnw -q test",
            "./mvnw -q -DskipTests compile",
            "./mvnw -q -DskipTests package",
            ".\\mvnw test",
            ".\\mvnw -q test",
            ".\\mvnw -q -DskipTests compile",
            ".\\mvnw -q -DskipTests package",
            "npm test",
            "npm run test",
            "npm run build",
            "pnpm test",
            "pnpm run test",
            "pnpm run build",
            "yarn test",
            "yarn build",
            "git status",
            "git diff",
            "git diff --stat"
    ));

    /**
     * 明确禁止出现的片段。
     */
    private List<String> deniedFragments = new ArrayList<>(List.of(

    ));

    /**
     * 不允许启动的交互式程序。
     */
    private List<String> deniedInteractiveCommands = new ArrayList<>(List.of(

    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    public int getMaxTimeoutSeconds() {
        return maxTimeoutSeconds;
    }

    public void setMaxTimeoutSeconds(int maxTimeoutSeconds) {
        this.maxTimeoutSeconds = maxTimeoutSeconds;
    }

    public int getMaxOutputChars() {
        return maxOutputChars;
    }

    public void setMaxOutputChars(int maxOutputChars) {
        this.maxOutputChars = maxOutputChars;
    }

    public List<String> getAllowlistedPrefixes() {
        return allowlistedPrefixes;
    }

    public void setAllowlistedPrefixes(List<String> allowlistedPrefixes) {
        this.allowlistedPrefixes = allowlistedPrefixes;
    }

    public List<String> getDeniedFragments() {
        return deniedFragments;
    }

    public void setDeniedFragments(List<String> deniedFragments) {
        this.deniedFragments = deniedFragments;
    }

    public List<String> getDeniedInteractiveCommands() {
        return deniedInteractiveCommands;
    }

    public void setDeniedInteractiveCommands(List<String> deniedInteractiveCommands) {
        this.deniedInteractiveCommands = deniedInteractiveCommands;
    }
}
