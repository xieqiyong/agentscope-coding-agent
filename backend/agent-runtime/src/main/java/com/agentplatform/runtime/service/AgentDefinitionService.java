package com.agentplatform.runtime.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.AgentEntity;
import com.agentplatform.persistence.repository.AgentRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Agent 定义管理服务。
 * 这里只管理配置，不处理一次运行中的临时状态。
 */
@Service
public class AgentDefinitionService {

    @Resource
    private AgentRepository agentRepository;

    /**
     * 查询全局可用 Agent；为空时创建默认 Coding Agent，保证聊天页有可选项。
     */
    @Transactional
    public List<AgentEntity> listOrCreateDefault() {
        List<AgentEntity> agents = agentRepository.findByStatusOrderByCreatedAtDesc("ENABLED");
        if (!agents.isEmpty()) {
            return agents;
        }
        createDefaultAgent();
        return agentRepository.findByStatusOrderByCreatedAtDesc("ENABLED");
    }

    /**
     * 创建自定义 Agent。
     */
    @Transactional
    public AgentEntity create(String name, String description, String systemPrompt,
                              String skillsJson, String mcpServicesJson, Integer maxIterations,
                              Integer timeoutSeconds, Long modelConfigId) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(400, "智能体名称不能为空");
        }
        // 同名防重：全局唯一，避免多套配置堆积
        agentRepository.findByName(name.trim()).ifPresent(existing -> {
            throw new BusinessException(409, "同名智能体已存在：" + existing.getName());
        });

        AgentEntity entity = new AgentEntity();
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setSystemPrompt(systemPrompt);
        entity.setSkillsJson(skillsJson);
        entity.setMcpServicesJson(mcpServicesJson);
        entity.setModelConfigId(modelConfigId);
        entity.setMaxIterations(firstPositive(maxIterations, 8));
        entity.setTimeoutSeconds(firstPositive(timeoutSeconds, 86400));
        entity.setStatus("ENABLED");
        return agentRepository.save(entity);
    }

    /**
     * 更新自定义 Agent 配置。
     */
    @Transactional
    public AgentEntity update(Long id, String name, String description, String systemPrompt,
                              String skillsJson, String mcpServicesJson, Integer maxIterations,
                              Integer timeoutSeconds, Long modelConfigId, String status) {
        AgentEntity entity = agentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "智能体不存在"));
        if (StringUtils.hasText(name)) {
            entity.setName(name.trim());
        }
        if (description != null) {
            entity.setDescription(description);
        }
        if (systemPrompt != null) {
            entity.setSystemPrompt(systemPrompt);
        }
        if (skillsJson != null) {
            entity.setSkillsJson(skillsJson);
        }
        if (mcpServicesJson != null) {
            entity.setMcpServicesJson(mcpServicesJson);
        }
        if (maxIterations != null) {
            entity.setMaxIterations(firstPositive(maxIterations, entity.getMaxIterations()));
        }
        if (timeoutSeconds != null) {
            entity.setTimeoutSeconds(firstPositive(timeoutSeconds, entity.getTimeoutSeconds()));
        }
        // modelConfigId 允许清空（null 表示回退默认模型），所以总是 set；前端会显式传值或 null。
        entity.setModelConfigId(modelConfigId);
        if (StringUtils.hasText(status)) {
            entity.setStatus(status.trim().toUpperCase());
        }
        return agentRepository.save(entity);
    }

    private AgentEntity createDefaultAgent() {
        AgentEntity entity = new AgentEntity();
        entity.setName("Coding Agent");
        entity.setDescription("默认编码智能体，适合读取项目、搜索代码、提出修改方案。");
        entity.setSystemPrompt("你是一个严谨的 Coding Agent。回答项目问题前要先读取和搜索工作区证据，修改代码时做最小改动。");
        entity.setSkillsJson("[]");
        entity.setMcpServicesJson("[]");
        entity.setMaxIterations(8);
        entity.setTimeoutSeconds(86400);
        entity.setStatus("ENABLED");
        return agentRepository.save(entity);
    }

    private int firstPositive(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }
}
