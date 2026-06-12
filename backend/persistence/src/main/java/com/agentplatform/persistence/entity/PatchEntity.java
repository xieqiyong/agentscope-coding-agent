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
 * Patch 实体：Agent 只能先生成修改方案，再由用户确认应用。
 */
@Getter
@Setter
@Entity
@Table(name = "patches")
public class PatchEntity extends BaseEntity {

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
     * patch 标题
     */
    @Column(name = "title", nullable = true, length = 256)
    private String title;
    /**
     * 修改摘要
     */
    @Lob
    @Column(name = "summary", nullable = true)
    private String summary;
    /**
     * 统一 diff 内容
     */
    @Lob
    @Column(name = "diff_text", nullable = false, columnDefinition = "LONGTEXT")
    private String diffText;
    /**
     * patch 状态
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    /**
     * 关联确认请求
     */
    @Column(name = "approval_request_id", nullable = true)
    private Long approvalRequestId;
    /**
     * 应用时间
     */
    @Column(name = "applied_at", nullable = true)
    private LocalDateTime appliedAt;
}
