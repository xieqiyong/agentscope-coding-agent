package com.agentplatform.runtime.agentscope;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentScope 工具权限配置。
 * 中文注释：这里控制“工具调用前是否需要用户确认”，真正的路径安全仍然由 sandbox 模块兜底。
 */
@Component
@ConfigurationProperties(prefix = "agent.runtime.permission")
public class AgentScopePermissionProperties {

    /**
     * 是否启用 AgentScope PermissionEngine。
     */
    private boolean enabled = true;

    /**
     * 是否让直接写文件类工具进入用户确认流程。
     */
    private boolean directWriteApprovalEnabled = true;

    /**
     * 需要确认的直接写入工具名。
     */
    private List<String> approvalRequiredTools = new ArrayList<>(
            List.of("write_file", "Write", "Edit", "apply_patch")
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
