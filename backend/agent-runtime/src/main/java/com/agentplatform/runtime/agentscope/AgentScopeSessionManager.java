package com.agentplatform.runtime.agentscope;

import com.agentplatform.runtime.model.RuntimeContext;
import io.agentscope.core.session.InMemorySession;
import io.agentscope.core.session.JsonSession;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.redis.RedisSession;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.SimpleSessionKey;
import io.lettuce.core.RedisClient;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.Locale;

/**
 * AgentScope Session 管理器。
 * 这里保存的是框架内部 AgentState，不替代 conversation_messages / agent_events 等平台数据。
 */
@Component
public class AgentScopeSessionManager {

    @Resource
    private AgentScopeSessionProperties properties;

    private volatile Session session;

    private volatile RedisClient redisClient;

    /**
     * 为一次运行绑定稳定的 sessionKey，并提前判断 Redis/Json 中是否已有 AgentState。
     */
    public AgentScopeSessionBinding bind(RuntimeContext context) {
        if (!properties.isEnabled()) {
            context.setAgentScopeSessionEnabled(false);
            context.setAgentScopeSessionType("disabled");
            return AgentScopeSessionBinding.disabled();
        }

        Session activeSession = getOrCreateSession();
        SessionKey sessionKey = SimpleSessionKey.of(buildSessionKey(context));
        boolean stateExists = activeSession.exists(sessionKey);

        context.setAgentScopeSessionEnabled(true);
        context.setAgentScopeSessionType(normalizeType());
        context.setAgentScopeSessionKey(sessionKey.toIdentifier());
        context.setAgentScopeStateExists(stateExists);

        return new AgentScopeSessionBinding(true, activeSession, sessionKey, stateExists, normalizeType());
    }

    private Session getOrCreateSession() {
        Session current = session;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (session == null) {
                session = createSession();
            }
            return session;
        }
    }

    private Session createSession() {
        String type = normalizeType();
        if ("redis".equals(type)) {
            return createRedisSession();
        }
        if ("json".equals(type)) {
            return createJsonSession();
        }
        if ("memory".equals(type)) {
            return new InMemorySession();
        }
        throw new IllegalArgumentException("不支持的 AgentScope Session 类型: " + properties.getType());
    }

    private Session createRedisSession() {
        String uri = properties.getRedis() != null ? properties.getRedis().getUri() : null;
        if (!StringUtils.hasText(uri)) {
            uri = "redis://127.0.0.1:6379/0";
        }
        redisClient = RedisClient.create(uri);
        return RedisSession.builder()
                .lettuceClient(redisClient)
                .keyPrefix(normalizeKeyPrefix(properties.getKeyPrefix()))
                .build();
    }

    private Session createJsonSession() {
        String path = properties.getJson() != null ? properties.getJson().getPath() : null;
        if (!StringUtils.hasText(path)) {
            path = "data/agentscope-sessions";
        }
        return new JsonSession(Path.of(path).toAbsolutePath().normalize());
    }

    /**
     * 主流做法是把 workspace、agent、conversation、user 都纳入 key。
     * 这样同一用户在同一会话里能恢复状态，不同项目和不同 Agent 之间不会串上下文。
     */
    private String buildSessionKey(RuntimeContext context) {
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
        String type = StringUtils.hasText(properties.getType()) ? properties.getType() : "redis";
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeKeyPrefix(String keyPrefix) {
        String prefix = StringUtils.hasText(keyPrefix) ? keyPrefix.trim() : "agent-platform:agentscope:session:";
        return prefix.endsWith(":") ? prefix : prefix + ":";
    }

    private String safeSegment(Object value) {
        String text = value == null ? "unknown" : String.valueOf(value);
        return text.replaceAll("[^a-zA-Z0-9_.:-]", "_");
    }

    @PreDestroy
    public void close() {
        Session current = session;
        if (current != null) {
            current.close();
        }
    }
}

class AgentScopeSessionBinding {

    private final boolean enabled;

    private final Session session;

    private final SessionKey sessionKey;

    private final boolean stateExists;

    private final String type;

    AgentScopeSessionBinding(boolean enabled, Session session, SessionKey sessionKey, boolean stateExists, String type) {
        this.enabled = enabled;
        this.session = session;
        this.sessionKey = sessionKey;
        this.stateExists = stateExists;
        this.type = type;
    }

    static AgentScopeSessionBinding disabled() {
        return new AgentScopeSessionBinding(false, null, null, false, "disabled");
    }

    boolean isEnabled() {
        return enabled;
    }

    Session getSession() {
        return session;
    }

    SessionKey getSessionKey() {
        return sessionKey;
    }

    boolean isStateExists() {
        return stateExists;
    }

    String getType() {
        return type;
    }
}
