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
 * 记忆冲突实体：记录已有记忆与候选记忆之间的冲突。
 */
@Getter
@Setter
@Entity
@Table(name = "memory_conflicts")
public class MemoryConflictEntity extends BaseEntity {

    /**
     * 工作区 ID
     */
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    /**
     * 已有记忆 ID
     */
    @Column(name = "existing_memory_id", nullable = false)
    private Long existingMemoryId;
    /**
     * 候选记忆 ID
     */
    @Column(name = "candidate_memory_id", nullable = false)
    private Long candidateMemoryId;
    /**
     * 冲突类型
     */
    @Column(name = "conflict_type", nullable = false, length = 64)
    private String conflictType;
    /**
     * 冲突状态
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    /**
     * 处理结果
     */
    @Column(name = "resolution", nullable = true, length = 64)
    private String resolution;
    /**
     * 解决时间
     */
    @Column(name = "resolved_at", nullable = true)
    private LocalDateTime resolvedAt;
}
