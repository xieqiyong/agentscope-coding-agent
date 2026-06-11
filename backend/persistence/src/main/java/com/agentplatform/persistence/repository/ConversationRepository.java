package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 会话实体：承载用户与 Agent 的一段连续上下文。
 */
public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {


    List<ConversationEntity> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);

    List<ConversationEntity> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(Long workspaceId, String status);

    List<ConversationEntity> findByAgentIdOrderByCreatedAtDesc(Long agentId);
}
