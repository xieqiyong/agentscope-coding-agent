package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 工具定义实体：描述可暴露给 Agent 的工具能力。
 */
@Getter
@Setter
@Entity
@Table(name = "tool_definitions")
public class ToolDefinitionEntity extends BaseEntity {

    /**
     * 工具名称
     */
    @Column(name = "name", nullable = false, length = 128)
    private String name;
    /**
     * 工具描述
     */
    @Column(name = "description", nullable = true, length = 512)
    private String description;
    /**
     * 工具类型
     */
    @Column(name = "tool_type", nullable = false, length = 64)
    private String toolType;
    /**
     * 工具入参 JSON Schema
     */
    @Lob
    @Column(name = "input_schema_json", nullable = true)
    private String inputSchemaJson;
    /**
     * 风险等级
     */
    @Column(name = "risk_level", nullable = false, length = 32)
    private String riskLevel;
    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
}
