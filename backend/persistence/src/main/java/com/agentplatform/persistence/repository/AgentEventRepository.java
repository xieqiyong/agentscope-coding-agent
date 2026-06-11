package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.AgentEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 事件实体：记录 SSE、AgentScope event 和 runtime trace。
 */
public interface AgentEventRepository extends JpaRepository<AgentEventEntity, Long> {


    List<AgentEventEntity> findByRunIdOrderByIdAsc(Long runId);
}
