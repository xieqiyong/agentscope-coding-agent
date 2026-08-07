package com.agentplatform.runtime.agentscope;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AgentScope harness 底座开关。
 *
 * harness 是 AgentScope 提供的 coding-agent 运行时底座，自带 workspace、filesystem 工具、
 * sandbox、subagent、memory compaction 等能力。开启后 AgentScopeRuntimeAdapter 会用
 * HarnessAgent 替代 ReActAgent，并使用 harness 自带的文件/命令工具。
 *
 * 当前为 PoC 阶段：默认关闭，开启时仅做最小只读验证（禁用 shell/memory/subagent/skill），
 * 不影响现有 ReActAgent 主路径。
 */
@Component
@ConfigurationProperties(prefix = "agent.runtime.harness")
public class AgentScopeHarnessProperties {

    /**
     * 是否启用 harness 底座。关闭时仍走原 ReActAgent + 手写工具路径。
     */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
