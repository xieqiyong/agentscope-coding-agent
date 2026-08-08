package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.AgentPlanStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 多 Agent 计划执行进度仓库。
 */
@Repository
public interface AgentPlanStateRepository extends JpaRepository<AgentPlanStateEntity, Long> {

    /**
     * 按会话查第一条处于指定状态的 plan 进度（用于定位可续接的 INTERRUPTED 记录或当前 RUNNING 记录）。
     */
    Optional<AgentPlanStateEntity> findFirstByConversationIdAndStatusInOrderByUpdatedAtDescIdDesc(
            Long conversationId, List<String> statuses);
}
