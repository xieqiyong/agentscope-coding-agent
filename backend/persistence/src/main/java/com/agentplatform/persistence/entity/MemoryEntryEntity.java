package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/**
 * 长期记忆实体：带来源、置信度、审核状态和冲突治理。
 */
@Getter
@Setter
@Entity
@Table(name = "memory_entries")
public class MemoryEntryEntity extends BaseEntity {

    /**
     * 工作区 ID
     */
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    /**
     * Agent ID，为空表示工作区通用记忆
     */
    @Column(name = "agent_id", nullable = true)
    private Long agentId;
    /**
     * 用户 ID
     */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    /**
     * 记忆类型
     */
    @Column(name = "memory_type", nullable = false, length = 64)
    private String memoryType;
    /**
     * 归一化 key
     */
    @Column(name = "normalized_key", nullable = false, length = 256)
    private String normalizedKey;
    /**
     * 记忆内容
     */
    @Lob
    @Column(name = "content", nullable = false)
    private String content;
    /**
     * 来源会话 ID
     */
    @Column(name = "source_conversation_id", nullable = true)
    private Long sourceConversationId;
    /**
     * 来源消息 ID
     */
    @Column(name = "source_message_id", nullable = true)
    private Long sourceMessageId;
    /**
     * 置信度
     */
    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;
    /**
     * 记忆状态
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    /**
     * 审核或冲突说明
     */
    @Lob
    @Column(name = "review_reason", nullable = true)
    private String reviewReason;
}
