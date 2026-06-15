package com.agentplatform.runtime.model;

/**
 * 用户处理工具审批的命令。
 * 中文注释：审批恢复不是新聊天消息，而是对某个 WAITING_APPROVAL run 的继续执行。
 */
public class AgentApprovalCommand {

    /**
     * 审批请求 ID。
     */
    private Long approvalRequestId;

    /**
     * 关联的 Agent Run ID，用于前端兜底校验。
     */
    private Long runId;

    /**
     * 是否批准工具调用。
     */
    private Boolean approved;

    /**
     * 决策人。
     */
    private String userId;

    /**
     * 本次恢复调用临时覆盖模型网关地址。
     */
    private String modelBaseUrl;

    /**
     * 本次恢复调用临时覆盖模型名称。
     */
    private String modelName;

    /**
     * 本次恢复调用临时覆盖 API Key。
     */
    private String apiKey;

    /**
     * 恢复执行超时时间，单位秒。
     */
    private Integer timeoutSeconds;

    /**
     * 是否透传 thinking 内容。
     */
    private Boolean traceThinkingContent;

    public Long getApprovalRequestId() {
        return approvalRequestId;
    }

    public void setApprovalRequestId(Long approvalRequestId) {
        this.approvalRequestId = approvalRequestId;
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getModelBaseUrl() {
        return modelBaseUrl;
    }

    public void setModelBaseUrl(String modelBaseUrl) {
        this.modelBaseUrl = modelBaseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Boolean getTraceThinkingContent() {
        return traceThinkingContent;
    }

    public void setTraceThinkingContent(Boolean traceThinkingContent) {
        this.traceThinkingContent = traceThinkingContent;
    }
}
