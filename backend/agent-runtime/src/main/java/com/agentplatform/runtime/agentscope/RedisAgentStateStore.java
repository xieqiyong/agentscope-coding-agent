package com.agentplatform.runtime.agentscope;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 基于 Redis 的 AgentScope AgentStateStore 实现。
 * AgentScope 2.0.0-RC3 core 只暴露 AgentStateStore 接口，这里由平台接管 Redis 持久化。
 */
public class RedisAgentStateStore implements AgentStateStore {

    private static final String STATE_FIELD_PREFIX = "state:";

    private static final String LIST_FIELD_PREFIX = "list:";

    private final String keyPrefix;

    private final RedisClient redisClient;

    private final StatefulRedisConnection<String, String> connection;

    private final RedisCommands<String, String> commands;

    public RedisAgentStateStore(String redisUri, String keyPrefix) {
        this(redisUri, keyPrefix, Duration.ofSeconds(3), Duration.ofSeconds(3));
    }

    public RedisAgentStateStore(String redisUri, String keyPrefix,
                                Duration connectTimeout, Duration commandTimeout) {
        this.keyPrefix = keyPrefix;
        RedisURI uri = RedisURI.create(redisUri);
        uri.setTimeout(connectTimeout);
        this.redisClient = RedisClient.create(uri);
        this.redisClient.setDefaultTimeout(commandTimeout);
        this.connection = redisClient.connect();
        this.commands = connection.sync();
    }

    @Override
    public void save(String agentId, String sessionId, String componentId, State state) {
        commands.hset(sessionKey(agentId, sessionId), stateField(componentId), JsonUtils.getJsonCodec().toJson(state));
        commands.sadd(sessionSetKey(agentId), sessionId);
    }

    @Override
    public void save(String agentId, String sessionId, String componentId, List<? extends State> states) {
        List<String> lines = new ArrayList<>();
        for (State state : states) {
            lines.add(JsonUtils.getJsonCodec().toJson(state));
        }
        commands.hset(sessionKey(agentId, sessionId), listField(componentId), String.join("\n", lines));
        commands.sadd(sessionSetKey(agentId), sessionId);
    }

    @Override
    public <T extends State> Optional<T> get(String agentId, String sessionId, String componentId, Class<T> stateType) {
        String json = commands.hget(sessionKey(agentId, sessionId), stateField(componentId));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(JsonUtils.getJsonCodec().fromJson(json, stateType));
    }

    @Override
    public <T extends State> List<T> getList(String agentId, String sessionId, String componentId, Class<T> stateType) {
        String content = commands.hget(sessionKey(agentId, sessionId), listField(componentId));
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<T> result = new ArrayList<>();
        for (String line : content.split("\\R")) {
            if (!line.isBlank()) {
                result.add(JsonUtils.getJsonCodec().fromJson(line, stateType));
            }
        }
        return result;
    }

    @Override
    public boolean exists(String agentId, String sessionId) {
        return commands.exists(sessionKey(agentId, sessionId)) > 0;
    }

    @Override
    public void delete(String agentId, String sessionId) {
        commands.del(sessionKey(agentId, sessionId));
        commands.srem(sessionSetKey(agentId), sessionId);
    }

    @Override
    public void delete(String agentId, String sessionId, String componentId) {
        String key = sessionKey(agentId, sessionId);
        commands.hdel(key, stateField(componentId), listField(componentId));
        if (commands.hlen(key) == 0) {
            delete(agentId, sessionId);
        }
    }

    @Override
    public Set<String> listSessionIds(String agentId) {
        return new LinkedHashSet<>(commands.smembers(sessionSetKey(agentId)));
    }

    @Override
    public void close() {
        connection.close();
        redisClient.shutdown();
    }

    private String sessionSetKey(String agentId) {
        return keyPrefix + "sessions:" + safeSegment(agentId);
    }

    private String sessionKey(String agentId, String sessionId) {
        return keyPrefix + "state:" + safeSegment(agentId) + ":" + safeSegment(sessionId);
    }

    private String stateField(String componentId) {
        return STATE_FIELD_PREFIX + safeSegment(componentId);
    }

    private String listField(String componentId) {
        return LIST_FIELD_PREFIX + safeSegment(componentId);
    }

    private String safeSegment(String value) {
        String text = value == null ? "unknown" : value;
        return text.replaceAll("[^a-zA-Z0-9_.:-]", "_");
    }
}
