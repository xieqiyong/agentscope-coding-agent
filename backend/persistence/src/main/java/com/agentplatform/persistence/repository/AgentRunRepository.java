package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.AgentRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 执行实体：一条记录代表一次用户请求触发的完整 Agent loop。
 */
public interface AgentRunRepository extends JpaRepository<AgentRunEntity, Long> {


    Optional<AgentRunEntity> findByTraceId(String traceId);

    List<AgentRunEntity> findByConversationIdOrderByStartedAtDesc(Long conversationId);

    List<AgentRunEntity> findByWorkspaceIdOrderByStartedAtDesc(Long workspaceId);
}
