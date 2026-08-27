package com.agentplatform.runtime.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.McpServiceEntity;
import com.agentplatform.persistence.repository.McpServiceRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * MCP 服务定义管理服务。
 * 当前只负责配置登记；实际连接与调用接入后复用这里的定义。
 */
@Service
public class McpServiceService {

    @Resource
    private McpServiceRepository mcpServiceRepository;

    /**
     * 查询 MCP 服务列表。
     */
    public List<McpServiceEntity> list() {
        return mcpServiceRepository.findAll().stream()
                .sorted((a, b) -> safe(a.getName()).compareToIgnoreCase(safe(b.getName())))
                .toList();
    }

    /**
     * 新建或更新 MCP 服务定义；id 为空时新建，同名服务会直接报冲突。
     */
    public McpServiceEntity save(Long id, String name, String description, String transportType, String endpoint, Boolean enabled) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(400, "服务名称不能为空");
        }
        if (!StringUtils.hasText(endpoint)) {
            throw new BusinessException(400, "服务地址不能为空");
        }
        McpServiceEntity entity;
        if (id != null) {
            entity = mcpServiceRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(404, "MCP 服务不存在"));
        } else {
            entity = new McpServiceEntity();
            if (mcpServiceRepository.findByName(name.trim()).isPresent()) {
                throw new BusinessException(409, "同名 MCP 服务已存在");
            }
        }
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setTransportType(StringUtils.hasText(transportType) ? transportType.trim() : "SSE");
        entity.setEndpoint(endpoint.trim());
        entity.setEnabled(enabled == null || enabled);
        return mcpServiceRepository.save(entity);
    }

    /**
     * 删除 MCP 服务定义。
     */
    public void delete(Long id) {
        if (id == null || !mcpServiceRepository.existsById(id)) {
            throw new BusinessException(404, "MCP 服务不存在");
        }
        mcpServiceRepository.deleteById(id);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
