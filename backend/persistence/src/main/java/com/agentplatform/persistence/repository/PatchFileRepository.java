package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.PatchFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Patch 文件实体：记录一个 patch 涉及的文件明细。
 */
public interface PatchFileRepository extends JpaRepository<PatchFileEntity, Long> {


    List<PatchFileEntity> findByPatchIdOrderByIdAsc(Long patchId);
}
