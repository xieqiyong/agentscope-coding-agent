package com.agentplatform.runtime.agentscope;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentScope 工具权限配置。
 * 中文注释：保留配置类兼容旧配置项；当前运行时不再使用工具审批规则。
 */
@Component
@ConfigurationProperties(prefix = "agent.runtime.permission")
public class AgentScopePermissionProperties {

    /**
     * 是否启用 AgentScope PermissionEngine。
     */
    private boolean enabled = false;

    /**
     * 是否让高风险工具进入用户确认流程。
     */
    private boolean directWriteApprovalEnabled = false;

    /**
     * 需要确认的高风险工具名。
     */
    private List<String> approvalRequiredTools = new ArrayList<>(
            List.of("Write", "Edit", "apply_patch")
    );

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDirectWriteApprovalEnabled() {
        return directWriteApprovalEnabled;
    }

    public void setDirectWriteApprovalEnabled(boolean directWriteApprovalEnabled) {
        this.directWriteApprovalEnabled = directWriteApprovalEnabled;
    }

    public List<String> getApprovalRequiredTools() {
        return approvalRequiredTools;
    }

    public void setApprovalRequiredTools(List<String> approvalRequiredTools) {
        this.approvalRequiredTools = approvalRequiredTools;
    }
}
