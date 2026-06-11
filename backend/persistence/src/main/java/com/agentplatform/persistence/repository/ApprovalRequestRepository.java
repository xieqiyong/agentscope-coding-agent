package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.ApprovalRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 用户确认实体：危险工具、执行命令和应用 patch 都要通过它治理。
 */
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequestEntity, Long> {


    List<ApprovalRequestEntity> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(Long workspaceId, String status);

    List<ApprovalRequestEntity> findByRunIdOrderByCreatedAtDesc(Long runId);
}
