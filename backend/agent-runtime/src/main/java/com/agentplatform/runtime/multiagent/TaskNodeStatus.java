package com.agentplatform.runtime.multiagent;

import java.util.Locale;

/**
 * 任务图节点状态。
 */
public enum TaskNodeStatus {
    PENDING("pending"),
    READY("ready"),
    RUNNING("in_progress"),
    COMPLETED("completed"),
    FAILED("failed"),
    WAITING("waiting"),
    CANCELLED("cancelled");

    private final String value;

    TaskNodeStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TaskNodeStatus from(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (TaskNodeStatus status : values()) {
            if (status.value.equals(normalized) || status.name().equalsIgnoreCase(normalized)) {
                return status;
            }
        }
        return PENDING;
    }
}
