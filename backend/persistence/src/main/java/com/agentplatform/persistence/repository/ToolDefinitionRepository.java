package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.ToolDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 工具定义实体：描述可暴露给 Agent 的工具能力。
 */
public interface ToolDefinitionRepository extends JpaRepository<ToolDefinitionEntity, Long> {


    Optional<ToolDefinitionEntity> findByName(String name);

    List<ToolDefinitionEntity> findByEnabledTrueOrderByNameAsc();
}
