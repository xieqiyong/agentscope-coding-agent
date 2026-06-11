package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.MemoryEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 长期记忆实体：带来源、置信度、审核状态和冲突治理。
 */
public interface MemoryEntryRepository extends JpaRepository<MemoryEntryEntity, Long> {


    List<MemoryEntryEntity> findByWorkspaceIdAndUserIdAndStatusOrderByUpdatedAtDesc(Long workspaceId, String userId, String status);

    List<MemoryEntryEntity> findByWorkspaceIdAndUserIdAndNormalizedKeyOrderByUpdatedAtDesc(Long workspaceId, String userId, String normalizedKey);

    List<MemoryEntryEntity> findBySourceMessageId(Long sourceMessageId);
}
