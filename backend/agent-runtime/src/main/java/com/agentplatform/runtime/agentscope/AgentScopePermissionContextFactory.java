package com.agentplatform.runtime.agentscope;

import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AgentScope 权限上下文工厂。
 * 中文注释：AgentScope 负责在工具调用前执行规则判断；平台负责配置规则、落库审批和前端确认。
 */
@Component
public class AgentScopePermissionContextFactory {

    private static final String POLICY_SOURCE = "agent-platform-policy";

    @Resource
    private AgentScopePermissionProperties properties;

    public PermissionContextState build() {
        PermissionContextState.Builder builder = PermissionContextState.builder()
                // BYPASS 表示默认放行；显式 ASK/DENY 规则仍然优先命中。
                .mode(PermissionMode.BYPASS);

        if (!properties.isEnabled()) {
            return builder.build();
        }

        if (properties.isDirectWriteApprovalEnabled() && properties.getApprovalRequiredTools() != null) {
            for (String toolName : properties.getApprovalRequiredTools()) {
                if (!StringUtils.hasText(toolName)) {
                    continue;
                }
                PermissionRule rule = new PermissionRule(
                        toolName.trim(),
                        null,
                        PermissionBehavior.ASK,
                        POLICY_SOURCE
                );
                builder.addAskRule(toolName.trim(), rule);
            }
        }

        return builder.build();
    }
}
