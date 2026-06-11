package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 模型配置实体：保存 AgentScope/OpenAI Compatible 模型调用参数。
 */
@Getter
@Setter
@Entity
@Table(name = "model_configs")
public class ModelConfigEntity extends BaseEntity {

    /**
     * 配置名称
     */
    @Column(name = "name", nullable = false, length = 128)
    private String name;
    /**
     * 模型供应商
     */
    @Column(name = "provider", nullable = false, length = 64)
    private String provider;
    /**
     * 模型网关地址
     */
    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;
    /**
     * 模型名称
     */
    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;
    /**
     * API Key 密文，不允许直接返回前端
     */
    @Lob
    @Column(name = "api_key_cipher", nullable = true)
    private String apiKeyCipher;
    /**
     * API Key 脱敏展示值
     */
    @Column(name = "api_key_mask", nullable = true, length = 64)
    private String apiKeyMask;
    /**
     * 是否默认配置
     */
    @Column(name = "is_default", nullable = false)
    private Boolean defaultConfig;
}
