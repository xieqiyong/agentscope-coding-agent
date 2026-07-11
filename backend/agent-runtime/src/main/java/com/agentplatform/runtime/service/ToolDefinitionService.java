package com.agentplatform.runtime.service;

import com.agentplatform.persistence.entity.ToolDefinitionEntity;
import com.agentplatform.persistence.repository.ToolDefinitionRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 工具定义查询服务。
 * 这里只读取平台已注册能力，不负责实际工具执行。
 */
@Service
public class ToolDefinitionService {

    @Resource
    private ToolDefinitionRepository toolDefinitionRepository;

    /**
     * 查询已启用的工具定义，可按工具类型过滤。
     */
    public List<ToolDefinitionEntity> listEnabled(List<String> toolTypes) {
        List<ToolDefinitionEntity> rows = toolDefinitionRepository.findByEnabledTrueOrderByNameAsc();
        Set<String> normalizedTypes = normalizeTypes(toolTypes);
        if (normalizedTypes.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> normalizedTypes.contains(safe(row.getToolType())))
                .toList();
    }

    private Set<String> normalizeTypes(List<String> toolTypes) {
        Set<String> normalized = new HashSet<>();
        if (toolTypes == null) {
            return normalized;
        }
        for (String toolType : toolTypes) {
            if (StringUtils.hasText(toolType)) {
                normalized.add(safe(toolType));
            }
        }
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
