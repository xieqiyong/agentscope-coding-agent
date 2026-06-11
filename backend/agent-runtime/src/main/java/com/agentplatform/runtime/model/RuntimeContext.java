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
 * 这里聚合的是运行所需数据，不在这里做数据库查询。
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
}

