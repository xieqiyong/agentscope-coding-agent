package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.ToolCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 工具调用实体：记录每次工具调用的参数、结果、状态和风险。
 */
public interface ToolCallRepository extends JpaRepository<ToolCallEntity, Long> {


    List<ToolCallEntity> findByRunIdOrderByStartedAtAsc(Long runId);
}
