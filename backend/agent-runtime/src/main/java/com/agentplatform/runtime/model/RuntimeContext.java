package com.agentplatform.runtime.model;

import com.agentplatform.persistence.entity.AgentEntity;
import com.agentplatform.persistence.entity.ConversationMessageEntity;
import com.agentplatform.persistence.entity.MemoryEntryEntity;
import com.agentplatform.persistence.entity.ModelConfigEntity;
import com.agentplatform.persistence.entity.WorkspaceEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次智能体执行期间需要的上下文快照。
 * 这里聚合的是运行所需数据，不在这个对象里做数据库查询。
 */
public class RuntimeContext {

    private AgentRunCommand command;
    private WorkspaceEntity workspace;
    private AgentEntity agent;
    private ModelConfigEntity modelConfig;
    private Long conversationId;
    private Long userMessageId;
    private Long runId;
    private String traceId;
    private String systemPrompt;
    private String modelBaseUrl;
    private String modelName;
    private String apiKey;
    private int maxIterations;
    private int timeoutSeconds;
    private List<ConversationMessageEntity> recentMessages = new ArrayList<>();
    private List<MemoryEntryEntity> activeMemories = new ArrayList<>();
    private RuntimeEventSink runtimeEventSink = RuntimeEventSink.noop();
    private long runStartedNanos;
    private boolean platformApprovalRequired;

    /**
     * AgentScope 内部状态是否启用。它只影响 AgentState 恢复，不影响数据库会话展示。
     */
    private boolean agentScopeSessionEnabled;

    /**
     * AgentScope Session 的实现类型，例如 redis/json/memory。
     */
    private String agentScopeSessionType;

    /**
     * 当前运行绑定的 AgentScope sessionKey。
     */
    private String agentScopeSessionKey;

    /**
     * 运行前检查到的 AgentState 是否已经存在。
     * 如果存在，本轮只需要把当前用户消息交给 AgentScope，历史上下文由 AgentScope 从 Session 恢复。
     */
    private boolean agentScopeStateExists;

    public AgentRunCommand getCommand() {
        return command;
    }

    public void setCommand(AgentRunCommand command) {
        this.command = command;
    }

    public WorkspaceEntity getWorkspace() {
        return workspace;
    }

    public void setWorkspace(WorkspaceEntity workspace) {
        this.workspace = workspace;
    }

    public AgentEntity getAgent() {
        return agent;
    }

    public void setAgent(AgentEntity agent) {
        this.agent = agent;
    }

    public ModelConfigEntity getModelConfig() {
        return modelConfig;
    }

    public void setModelConfig(ModelConfigEntity modelConfig) {
        this.modelConfig = modelConfig;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getUserMessageId() {
        return userMessageId;
    }

    public void setUserMessageId(Long userMessageId) {
        this.userMessageId = userMessageId;
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
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

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<ConversationMessageEntity> getRecentMessages() {
        return recentMessages;
    }

    public void setRecentMessages(List<ConversationMessageEntity> recentMessages) {
        this.recentMessages = recentMessages;
    }

    public List<MemoryEntryEntity> getActiveMemories() {
        return activeMemories;
    }

    public void setActiveMemories(List<MemoryEntryEntity> activeMemories) {
        this.activeMemories = activeMemories;
    }

    public RuntimeEventSink getRuntimeEventSink() {
        return runtimeEventSink;
    }

    public void setRuntimeEventSink(RuntimeEventSink runtimeEventSink) {
        this.runtimeEventSink = runtimeEventSink != null ? runtimeEventSink : RuntimeEventSink.noop();
    }

    public long getRunStartedNanos() {
        return runStartedNanos;
    }

    public void setRunStartedNanos(long runStartedNanos) {
        this.runStartedNanos = runStartedNanos;
    }

    public boolean isPlatformApprovalRequired() {
        return platformApprovalRequired;
    }

    public void setPlatformApprovalRequired(boolean platformApprovalRequired) {
        this.platformApprovalRequired = platformApprovalRequired;
    }

    public boolean isAgentScopeSessionEnabled() {
        return agentScopeSessionEnabled;
    }

    public void setAgentScopeSessionEnabled(boolean agentScopeSessionEnabled) {
        this.agentScopeSessionEnabled = agentScopeSessionEnabled;
    }

    public String getAgentScopeSessionType() {
        return agentScopeSessionType;
    }

    public void setAgentScopeSessionType(String agentScopeSessionType) {
        this.agentScopeSessionType = agentScopeSessionType;
    }

    public String getAgentScopeSessionKey() {
        return agentScopeSessionKey;
    }

    public void setAgentScopeSessionKey(String agentScopeSessionKey) {
        this.agentScopeSessionKey = agentScopeSessionKey;
    }

    public boolean isAgentScopeStateExists() {
        return agentScopeStateExists;
    }

    public void setAgentScopeStateExists(boolean agentScopeStateExists) {
        this.agentScopeStateExists = agentScopeStateExists;
    }
}
