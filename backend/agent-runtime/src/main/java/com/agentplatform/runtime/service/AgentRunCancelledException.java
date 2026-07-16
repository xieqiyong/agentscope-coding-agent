package com.agentplatform.runtime.service;

/**
 * Agent Run 被用户取消时抛出的运行期异常。
 */
public class AgentRunCancelledException extends RuntimeException {

    public AgentRunCancelledException(String message) {
        super(message);
    }
}
