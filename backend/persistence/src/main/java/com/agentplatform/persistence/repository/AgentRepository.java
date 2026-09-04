package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.AgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 定义实体：只保存配置，不保存一次运行中的临时状态。
 */
public interface AgentRepository extends JpaRepository<AgentEntity, Long> {


    List<AgentEntity> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);

    List<AgentEntity> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(Long workspaceId, String status);

    /** 全局按状态查询智能体：智能体不再绑定工作区 */
    List<AgentEntity> findByStatusOrderByCreatedAtDesc(String status);
}
