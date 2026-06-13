package com.agentplatform.runtime.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.enums.AgentRunStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Agent Run 状态机。
 * 这里只定义“能不能从 A 状态进入 B 状态”，不直接操作数据库。
 */
@Component
public class AgentRunStateMachine {

    private static final Map<AgentRunStatus, Set<AgentRunStatus>> TRANSITIONS = Map.of(
            AgentRunStatus.RUNNING, Set.of(
                    AgentRunStatus.WAITING_APPROVAL,
                    AgentRunStatus.COMPLETED,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.TIMEOUT,
                    AgentRunStatus.CANCELLED
            ),
            AgentRunStatus.WAITING_APPROVAL, Set.of(
                    AgentRunStatus.RUNNING,
                    AgentRunStatus.COMPLETED,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.TIMEOUT,
                    AgentRunStatus.CANCELLED
            )
    );

    public void assertCanTransit(AgentRunStatus source, AgentRunStatus target) {
        if (source == target) {
            return;
        }
        if (source != null && source.isTerminal()) {
            throw new BusinessException(409, "Agent Run 已进入终态，不能从 " + source + " 流转到 " + target);
        }
        Set<AgentRunStatus> targets = TRANSITIONS.getOrDefault(source, Set.of());
        if (!targets.contains(target)) {
            throw new BusinessException(409, "非法 Agent Run 状态流转：" + source + " -> " + target);
        }
    }
}
