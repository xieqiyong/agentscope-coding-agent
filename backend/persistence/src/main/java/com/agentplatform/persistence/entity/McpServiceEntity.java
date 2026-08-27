package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * MCP 服务定义实体：登记外部 MCP 服务的连接信息。
 * 当前只做配置登记与展示，实际调用接入后由运行时按此配置连接。
 */
@Getter
@Setter
@Entity
@Table(name = "mcp_services")
public class McpServiceEntity extends BaseEntity {

    /**
     * 服务名称，全局唯一
     */
    @Column(name = "name", nullable = false, unique = true, length = 128)
    private String name;

    /**
     * 服务描述
     */
    @Column(name = "description", nullable = true, length = 512)
    private String description;

    /**
     * 传输类型：SSE / STREAMABLE_HTTP / STDIO
     */
    @Column(name = "transport_type", nullable = false, length = 32)
    private String transportType;

    /**
     * 连接地址（SSE/HTTP 为 URL，STDIO 为启动命令）
     */
    @Column(name = "endpoint", nullable = false, length = 1024)
    private String endpoint;

    /**
     * 状态：ENABLED/DISABLED
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
}
