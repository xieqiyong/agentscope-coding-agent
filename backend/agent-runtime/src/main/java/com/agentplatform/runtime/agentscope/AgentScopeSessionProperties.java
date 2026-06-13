package com.agentplatform.runtime.agentscope;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AgentScope 内部状态存储配置。
 * 数据库仍然负责页面会话和审计记录，这里只控制 ReActAgent 的 AgentState 持久化方式。
 */
@Component
@ConfigurationProperties(prefix = "agent.runtime.session")
public class AgentScopeSessionProperties {

    /**
     * 是否启用 AgentScope Session。关闭后每次运行都只依赖平台数据库重建外部上下文。
     */
    private boolean enabled = true;

    /**
     * 状态存储类型：redis / json / memory。
     * 生产和面试主线推荐 redis，本地临时验证可以切到 json。
     */
    private String type = "redis";

    /**
     * Redis key 前缀。最终 key 会继续拼接 AgentScope 自己的字段。
     */
    private String keyPrefix = "agent-platform:agentscope:session:";

    private Redis redis = new Redis();

    private Json json = new Json();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public Json getJson() {
        return json;
    }

    public void setJson(Json json) {
        this.json = json;
    }

    public static class Redis {

        /**
         * Lettuce 使用的 Redis URI，例如 redis://127.0.0.1:6379/0。
         */
        private String uri = "redis://127.0.0.1:6379/0";

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }
    }

    public static class Json {

        /**
         * JsonSession 的本地存储目录，主要用于没有 Redis 的开发环境。
         */
        private String path = "data/agentscope-sessions";

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
