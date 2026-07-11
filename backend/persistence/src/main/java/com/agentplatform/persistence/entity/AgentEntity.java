package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Agent 定义实体：只保存配置，不保存一次运行中的临时状态。
 */
@Getter
@Setter
@Entity
@Table(name = "agents")
public class AgentEntity extends BaseEntity {

    /**
     * 所属工作区 ID
     */
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    /**
     * Agent 名称
     */
    @Column(name = "name", nullable = false, length = 128)
    private String name;
    /**
     * Agent 描述
     */
    @Column(name = "description", nullable = true, length = 512)
    private String description;
    /**
     * 系统提示词
     */
    @Lob
    @Column(name = "system_prompt", nullable = true)
    private String systemPrompt;
    /**
     * 绑定的 Skills 配置 JSON。
     */
    @Lob
    @Column(name = "skills_json", nullable = true, columnDefinition = "LONGTEXT")
    private String skillsJson;
    /**
     * 绑定的 MCP 服务配置 JSON。
     */
    @Lob
    @Column(name = "mcp_services_json", nullable = true, columnDefinition = "LONGTEXT")
    private String mcpServicesJson;
    /**
     * 模型配置 ID
     */
    @Column(name = "model_config_id", nullable = true)
    private Long modelConfigId;
    /**
     * 最大循环次数
     */
    @Column(name = "max_iterations", nullable = false)
    private Integer maxIterations;
    /**
     * 执行超时时间
     */
    @Column(name = "timeout_seconds", nullable = false)
    private Integer timeoutSeconds;
    /**
     * 状态：ENABLED/DISABLED
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;
}
