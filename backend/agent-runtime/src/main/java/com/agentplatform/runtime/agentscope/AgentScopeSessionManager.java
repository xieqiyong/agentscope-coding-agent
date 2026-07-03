package com.agentplatform.runtime.agentscope;

import com.agentplatform.runtime.model.RuntimeContext;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * AgentScope Session 管理器。
 * 这里保存的是框架内部 AgentState，不替代 conversation_messages / agent_events 等平台数据。
 */
@Component
public class AgentScopeSessionManager {

    private static final String AGENT_STATE_STORE_ID = "coding-agent";

    @Resource
    private AgentScopeSessionProperties properties;

    private volatile AgentStateStore stateStore;

    /**
     * 为一次运行绑定稳定的 sessionId，并提前判断状态存储中是否已有 AgentState。
     */
    public AgentScopeSessionBinding bind(RuntimeContext context) {
        if (!properties.isEnabled()) {
            context.setAgentScopeSessionEnabled(false);
            context.setAgentScopeSessionType("disabled");
            return AgentScopeSessionBinding.disabled();
        }

        AgentStateStore activeStateStore = getOrCreateStateStore();
        String sessionId = buildSessionId(context);
        boolean stateExists = activeStateStore.exists(AGENT_STATE_STORE_ID, sessionId);

        context.setAgentScopeSessionEnabled(true);
        context.setAgentScopeSessionType(normalizeType());
        context.setAgentScopeSessionKey(sessionId);
        context.setAgentScopeStateExists(stateExists);

        return new AgentScopeSessionBinding(true, activeStateStore, sessionId, stateExists, normalizeType());
    }

    private AgentStateStore getOrCreateStateStore() {
        AgentStateStore current = stateStore;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (stateStore == null) {
                stateStore = createStateStore();
            }
            return stateStore;
        }
    }

    private AgentStateStore createStateStore() {
        String type = normalizeType();
        if ("json".equals(type)) {
            return createJsonStateStore();
        }
        if ("memory".equals(type)) {
            return new InMemoryAgentStateStore();
        }
        if ("redis".equals(type)) {
            return createRedisStateStore();
        }
        throw new IllegalArgumentException("不支持的 AgentScope 状态存储类型: " + properties.getType());
    }

    private AgentStateStore createRedisStateStore() {
        AgentScopeSessionProperties.Redis redis = properties.getRedis();
        String uri = redis != null ? redis.getUri() : null;
        if (!StringUtils.hasText(uri)) {
            uri = "redis://127.0.0.1:6379/0";
        }
        long connectTimeoutSeconds = redis != null ? redis.getConnectTimeoutSeconds() : 3;
        long commandTimeoutSeconds = redis != null ? redis.getCommandTimeoutSeconds() : 3;
        return new RedisAgentStateStore(uri, normalizeKeyPrefix(properties.getKeyPrefix()),
                positiveDuration(connectTimeoutSeconds, 3), positiveDuration(commandTimeoutSeconds, 3));
    }

    private AgentStateStore createJsonStateStore() {
        String path = properties.getJson() != null ? properties.getJson().getPath() : null;
        if (!StringUtils.hasText(path)) {
            path = "data/agentscope-sessions";
        }
        return new JsonFileAgentStateStore(Path.of(path).toAbsolutePath().normalize());
    }

    /**
     * 主流做法是把 workspace、agent、conversation、user 都纳入 key。
     * 这样同一用户在同一会话里能恢复状态，不同项目和不同 Agent 之间不会串上下文。
     */
    private String buildSessionId(RuntimeContext context) {
        Long workspaceId = context.getWorkspace() != null ? context.getWorkspace().getId() : context.getCommand().getWorkspaceId();
        Long agentId = context.getAgent() != null ? context.getAgent().getId() : context.getCommand().getAgentId();
        String userId = StringUtils.hasText(context.getCommand().getUserId()) ? context.getCommand().getUserId() : "default";
        return "workspace:%s:agent:%s:conversation:%s:user:%s".formatted(
                safeSegment(workspaceId),
                safeSegment(agentId),
                safeSegment(context.getConversationId()),
                safeSegment(userId)
        );
    }

    private String normalizeType() {
        String type = StringUtils.hasText(properties.getType()) ? properties.getType() : "json";
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeKeyPrefix(String keyPrefix) {
        String prefix = StringUtils.hasText(keyPrefix) ? keyPrefix.trim() : "agent-platform:agentscope:session:";
        return prefix.endsWith(":") ? prefix : prefix + ":";
    }

    private Duration positiveDuration(long seconds, long defaultSeconds) {
        return Duration.ofSeconds(seconds > 0 ? seconds : defaultSeconds);
    }

    private String safeSegment(Object value) {
        String text = value == null ? "unknown" : String.valueOf(value);
        return text.replaceAll("[^a-zA-Z0-9_.:-]", "_");
    }

    @PreDestroy
    public void close() {
        AgentStateStore current = stateStore;
        if (current != null) {
            current.close();
        }
    }
}

class AgentScopeSessionBinding {

    private final boolean enabled;

    private final AgentStateStore stateStore;

    private final String sessionId;

    private final boolean stateExists;

    private final String type;

    AgentScopeSessionBinding(boolean enabled, AgentStateStore stateStore, String sessionId, boolean stateExists, String type) {
        this.enabled = enabled;
        this.stateStore = stateStore;
        this.sessionId = sessionId;
        this.stateExists = stateExists;
        this.type = type;
    }

    static AgentScopeSessionBinding disabled() {
        return new AgentScopeSessionBinding(false, null, null, false, "disabled");
    }

    boolean isEnabled() {
        return enabled;
    }

    AgentStateStore getStateStore() {
        return stateStore;
    }

    String getSessionId() {
        return sessionId;
    }

    boolean isStateExists() {
        return stateExists;
    }

    String getType() {
        return type;
    }
}
