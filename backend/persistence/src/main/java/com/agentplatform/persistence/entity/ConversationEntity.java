package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话实体：承载用户与 Agent 的一段连续上下文。
 */
@Getter
@Setter
@Entity
@Table(name = "conversations")
public class ConversationEntity extends BaseEntity {

    /**
     * 所属工作区 ID
     */
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    /**
     * 绑定的 Agent ID
     */
    @Column(name = "agent_id", nullable = true)
    private Long agentId;
    /**
     * 会话标题
     */
    @Column(name = "title", nullable = false, length = 256)
    private String title;
    /**
     * 状态：ACTIVE/ARCHIVED/DELETED
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;
}
