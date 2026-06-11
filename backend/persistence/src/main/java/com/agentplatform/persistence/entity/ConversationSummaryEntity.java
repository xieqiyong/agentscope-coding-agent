package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话摘要实体：用于滑动窗口之外的短期压缩记忆。
 */
@Getter
@Setter
@Entity
@Table(name = "conversation_summaries")
public class ConversationSummaryEntity extends BaseEntity {

    /**
     * 会话 ID
     */
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;
    /**
     * 摘要起始消息 ID
     */
    @Column(name = "from_message_id", nullable = false)
    private Long fromMessageId;
    /**
     * 摘要结束消息 ID
     */
    @Column(name = "to_message_id", nullable = false)
    private Long toMessageId;
    /**
     * 摘要内容
     */
    @Lob
    @Column(name = "summary", nullable = false)
    private String summary;
    /**
     * 摘要 token 数
     */
    @Column(name = "token_count", nullable = false)
    private Integer tokenCount;
    /**
     * 状态：ACTIVE/SUPERSEDED
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;
}
