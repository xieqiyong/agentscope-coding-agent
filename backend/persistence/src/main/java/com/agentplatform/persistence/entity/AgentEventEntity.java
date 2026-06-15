package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Agent 事件实体：记录 SSE、AgentScope event 和 runtime trace。
 */
@Getter
@Setter
@Entity
@Table(name = "agent_events")
public class AgentEventEntity extends BaseEntity {

    /**
     * Agent 执行 ID
     */
    @Column(name = "run_id", nullable = false)
    private Long runId;
    /**
     * 事件类型
     */
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;
    /**
     * 事件阶段
     */
    @Column(name = "stage", nullable = true, length = 64)
    private String stage;
    /**
     * 事件正文
     */
    @Lob
    @Column(name = "content", nullable = true, columnDefinition = "LONGTEXT")
    private String content;
    /**
     * 事件元数据 JSON
     */
    @Lob
    @Column(name = "metadata_json", nullable = true, columnDefinition = "LONGTEXT")
    private String metadataJson;
    /**
     * 距离执行开始的毫秒数
     */
    @Column(name = "elapsed_ms", nullable = false)
    private Long elapsedMs;
}
