package com.agentplatform.persistence.enums;

/**
 * 持久化状态枚举：用于约束数据库状态字段的推荐取值。
 */
public enum ToolCallStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    WAITING_APPROVAL,
    DENIED
}
