package com.agentplatform.runtime.model;

/**
 * 一次智能体执行的最终结果。
 */
public class AgentRunResult {

    private Long runId;
    private Long conversationId;
    private String traceId;
    private String answer;
    private int inputTokens;
    private int outputTokens;
    private int modelCallCount;
    private String status;

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }

    public int getModelCallCount() {
        return modelCallCount;
    }

    public void setModelCallCount(int modelCallCount) {
        this.modelCallCount = modelCallCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

