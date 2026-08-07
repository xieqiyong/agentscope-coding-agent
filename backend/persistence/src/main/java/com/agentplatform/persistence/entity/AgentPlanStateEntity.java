package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 多 Agent 计划执行进度持久化。
 * 一个会话至多一条活跃记录（RUNNING / INTERRUPTED），用于中断后从断点续接执行。
 */
@Getter
@Setter
@Entity
@Table(name = "agent_plan_state")
public class AgentPlanStateEntity extends BaseEntity {

    /**
     * 会话 ID
     */
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /**
     * 关联的 Agent Run ID（首次生成该计划的 run）
     */
    @Column(name = "run_id", nullable = true)
    private Long runId;

    /**
     * 序列化的 AgentPlan JSON，续接时还原为计划结构
     */
    @Lob
    @Column(name = "plan_json", nullable = false, columnDefinition = "LONGTEXT")
    private String planJson;

    /**
     * 下一步要执行的步骤索引；[0, nextStepIndex) 视为已完成
     */
    @Column(name = "next_step_index", nullable = false)
    private Integer nextStepIndex;

    /**
     * 状态：RUNNING（执行中）/ INTERRUPTED（被中断，可续接）/ COMPLETED（全部完成）
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;
}
