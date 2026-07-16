package com.agentplatform.runtime.agentscope;

import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import org.springframework.stereotype.Component;

/**
 * AgentScope 权限上下文工厂。
 * 中文注释：当前只做 workspace 级校验，不再注册工具审批规则。
 */
@Component
public class AgentScopePermissionContextFactory {

    public PermissionContextState build() {
        return PermissionContextState.builder()
                .mode(PermissionMode.BYPASS)
                .build();
    }
}
