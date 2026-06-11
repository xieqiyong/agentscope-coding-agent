package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话消息实体：短期记忆的原始来源。
 */
@Getter
@Setter
@Entity
@Table(name = "conversation_messages")
public class ConversationMessageEntity extends BaseEntity {

    /**
     * 会话 ID
     */
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;
    /**
     * 角色：SYSTEM/USER/ASSISTANT/TOOL
     */
    @Column(name = "role", nullable = false, length = 32)
    private String role;
    /**
     * 消息正文
     */
    @Lob
    @Column(name = "content", nullable = false)
    private String content;
    /**
     * token 数
     */
    @Column(name = "token_count", nullable = false)
    private Integer tokenCount;
    /**
     * 消息扩展信息 JSON
     */
    @Lob
    @Column(name = "metadata_json", nullable = true)
    private String metadataJson;
}
