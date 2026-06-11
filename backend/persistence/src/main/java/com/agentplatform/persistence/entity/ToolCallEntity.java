package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 工具调用实体：记录每次工具调用的参数、结果、状态和风险。
 */
@Getter
@Setter
@Entity
@Table(name = "tool_calls")
public class ToolCallEntity extends BaseEntity {

    /**
     * Agent 执行 ID
     */
    @Column(name = "run_id", nullable = false)
    private Long runId;
    /**
     * 工具名称
     */
    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;
    /**
     * 工具入参 JSON
     */
    @Lob
    @Column(name = "arguments_json", nullable = true)
    private String argumentsJson;
    /**
     * 工具结果
     */
    @Lob
    @Column(name = "result", nullable = true)
    private String result;
    /**
     * 调用状态
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    /**
     * 风险等级
     */
    @Column(name = "risk_level", nullable = true, length = 32)
    private String riskLevel;
    /**
     * 关联确认请求
     */
    @Column(name = "approval_request_id", nullable = true)
    private Long approvalRequestId;
    /**
     * 开始时间
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
    /**
     * 结束时间
     */
    @Column(name = "finished_at", nullable = true)
    private LocalDateTime finishedAt;
    /**
     * 失败原因
     */
    @Lob
    @Column(name = "error_message", nullable = true)
    private String errorMessage;
}
