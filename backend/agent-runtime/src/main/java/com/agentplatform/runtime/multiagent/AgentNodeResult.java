package com.agentplatform.runtime.multiagent;

/**
 * Agent 节点执行结果。
 */
public class AgentNodeResult {

    private boolean success;
    private String message;

    public static AgentNodeResult success(String message) {
        AgentNodeResult result = new AgentNodeResult();
        result.setSuccess(true);
        result.setMessage(message);
        return result;
    }

    public static AgentNodeResult failed(String message) {
        AgentNodeResult result = new AgentNodeResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
