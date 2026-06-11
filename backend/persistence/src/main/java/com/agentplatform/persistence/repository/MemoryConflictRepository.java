package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.MemoryConflictEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 记忆冲突实体：记录已有记忆与候选记忆之间的冲突。
 */
public interface MemoryConflictRepository extends JpaRepository<MemoryConflictEntity, Long> {


    List<MemoryConflictEntity> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(Long workspaceId, String status);

    List<MemoryConflictEntity> findByCandidateMemoryId(Long candidateMemoryId);

    List<MemoryConflictEntity> findByExistingMemoryId(Long existingMemoryId);
}
