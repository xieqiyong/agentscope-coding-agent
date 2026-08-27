package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.SkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Skill 定义仓库。
 */
public interface SkillRepository extends JpaRepository<SkillEntity, Long> {

    Optional<SkillEntity> findByName(String name);

    List<SkillEntity> findByEnabledTrueOrderByNameAsc();

    List<SkillEntity> findByNameInAndEnabledTrue(Collection<String> names);
}
