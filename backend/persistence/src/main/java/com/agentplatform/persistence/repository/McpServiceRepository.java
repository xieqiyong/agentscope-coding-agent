package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.McpServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * MCP 服务定义仓库。
 */
public interface McpServiceRepository extends JpaRepository<McpServiceEntity, Long> {

    Optional<McpServiceEntity> findByName(String name);
}
