package com.agentplatform.persistence.enums;

/**
 * 持久化状态枚举：用于约束数据库状态字段的推荐取值。
 */
public enum AgentRunStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    WAITING_APPROVAL,
    TIMEOUT;

    /**
     * 中文注释：终态表示一次运行已经收口，后续不允许再恢复成运行中，避免旧 checkpoint 被重复执行。
     */
    public boolean isTerminal() {
        return this == COMPLETED
                || this == FAILED
                || this == CANCELLED
                || this == TIMEOUT;
    }

    public static AgentRunStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AgentRunStatus 不能为空");
        }
        return AgentRunStatus.valueOf(value.trim().toUpperCase());
    }
}
