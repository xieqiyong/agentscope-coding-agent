package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.PatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Patch 实体：Agent 只能先生成修改方案，再由用户确认应用。
 */
public interface PatchRepository extends JpaRepository<PatchEntity, Long> {


    List<PatchEntity> findByRunIdOrderByCreatedAtDesc(Long runId);

    List<PatchEntity> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);
}
