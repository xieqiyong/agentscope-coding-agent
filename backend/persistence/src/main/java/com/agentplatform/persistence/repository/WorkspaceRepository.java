package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.WorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 工作区实体：定义 coding agent 允许操作的根目录。
 */
public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, Long> {


    Optional<WorkspaceEntity> findByRootPath(String rootPath);

    List<WorkspaceEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
}
