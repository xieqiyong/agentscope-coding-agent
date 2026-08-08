package com.agentplatform.persistence.entity;

import com.agentplatform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 多 Agent 任务图执行进度持久化。
 * planJson 保存节点依赖、状态和输出，用于中断后按依赖关系续接执行。
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
     * 已完成节点数量。字段名为兼容旧表保留，旧线性计划仍把它作为续接索引。
     */
    @Column(name = "next_step_index", nullable = false)
    private Integer nextStepIndex;

    /**
     * 状态：RUNNING / WAITING_APPROVAL / INTERRUPTED / COMPLETED / FAILED
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;
}
