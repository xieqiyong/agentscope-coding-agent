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
 * 用户确认实体：危险工具、执行命令和应用 patch 都要通过它治理。
 */
@Getter
@Setter
@Entity
@Table(name = "approval_requests")
public class ApprovalRequestEntity extends BaseEntity {

    /**
     * Agent 执行 ID
     */
    @Column(name = "run_id", nullable = false)
    private Long runId;
    /**
     * 工作区 ID
     */
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    /**
     * 确认类型
     */
    @Column(name = "request_type", nullable = false, length = 64)
    private String requestType;
    /**
     * 确认标题
     */
    @Column(name = "title", nullable = false, length = 256)
    private String title;
    /**
     * 确认详情 JSON
     */
    @Lob
    @Column(name = "detail_json", nullable = true)
    private String detailJson;
    /**
     * 确认状态
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    /**
     * 决策人
     */
    @Column(name = "decided_by", nullable = true, length = 64)
    private String decidedBy;
    /**
     * 决策时间
     */
    @Column(name = "decided_at", nullable = true)
    private LocalDateTime decidedAt;
}
