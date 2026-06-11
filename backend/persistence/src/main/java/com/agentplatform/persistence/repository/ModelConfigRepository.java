package com.agentplatform.persistence.repository;

import com.agentplatform.persistence.entity.ModelConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 模型配置实体：保存 AgentScope/OpenAI Compatible 模型调用参数。
 */
public interface ModelConfigRepository extends JpaRepository<ModelConfigEntity, Long> {


    Optional<ModelConfigEntity> findByName(String name);

    List<ModelConfigEntity> findByDefaultConfigTrue();
}
