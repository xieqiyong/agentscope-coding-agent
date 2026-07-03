package com.agentplatform.runtime.model;

import java.util.Map;

/**
 * 启动一次智能体执行的命令。
 * 这个对象承载前端发起聊天时传入的必要上下文。
 */
public class AgentRunCommand {

    /**
     * 工作区 ID，新建会话时必须传。
     */
    private Long workspaceId;

    /**
     * 智能体 ID，新建会话时必须传。
     */
    private Long agentId;

    /**
     * 会话 ID，续聊时传；如果为空则自动创建新会话。
     */
    private Long conversationId;

    /**
     * 用户 ID，用于查询长期记忆。
     */
    private String userId;

    /**
     * 用户本轮输入。
     */
    private String message;

    /**
     * 运行模式。默认 AUTO；/plan 会转换成 PLAN_ONLY，计划卡片执行会转换成 PLAN_EXECUTE。
     */
    private String runMode;

    /**
     * 前端计划卡片回传的结构化计划。
     * 中文注释：执行计划时用于 Orchestrator 更新步骤状态，真正执行仍以 message 中的明确指令为准。
     */
    private Map<String, Object> plan;

    /**
     * 新会话标题，可为空。
     */
    private String title;

    /**
     * 本次调用临时覆盖模型网关地址。
     */
    private String modelBaseUrl;

    /**
     * 本次调用临时覆盖模型名称。
     */
    private String modelName;

    /**
     * 本次调用临时覆盖 API Key。
     */
    private String apiKey;

    /**
     * 本次智能体循环最大迭代次数。
     */
    private Integer maxIterations;

    /**
     * 本次执行总超时时间，单位秒。
     */
    private Integer timeoutSeconds;

    /**
     * 是否把模型返回的 thinking 内容透传给前端。
     * 默认不透传，只显示“正在思考”等阶段事件。
     */
    private Boolean traceThinkingContent;

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRunMode() {
        return runMode;
    }

    public void setRunMode(String runMode) {
        this.runMode = runMode;
    }

    public Map<String, Object> getPlan() {
        return plan;
    }

    public void setPlan(Map<String, Object> plan) {
        this.plan = plan;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public Integer getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
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
