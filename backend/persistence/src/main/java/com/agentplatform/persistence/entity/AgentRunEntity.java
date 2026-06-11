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
 * Agent 执行实体：一条记录代表一次用户请求触发的完整 Agent loop。
 */
@Getter
@Setter
@Entity
@Table(name = "agent_runs")
public class AgentRunEntity extends BaseEntity {

    /**
     * 追踪 ID
     */
    @Column(name = "trace_id", nullable = false, length = 256)
    private String traceId;
    /**
     * 会话 ID
     */
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;
    /**
     * Agent ID
     */
    @Column(name = "agent_id", nullable = false)
    private Long agentId;
    /**
     * 工作区 ID
     */
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;
    /**
     * 触发执行的用户消息 ID
     */
    @Column(name = "user_message_id", nullable = true)
    private Long userMessageId;
    /**
     * 执行状态
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    /**
     * 失败原因
     */
    @Lob
    @Column(name = "error_message", nullable = true, columnDefinition = "LONGTEXT")
    private String errorMessage;
    /**
     * 输入 token
     */
    @Column(name = "input_tokens", nullable = false)
    private Integer inputTokens;
    /**
     * 输出 token
     */
    @Column(name = "output_tokens", nullable = false)
    private Integer outputTokens;
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
}
