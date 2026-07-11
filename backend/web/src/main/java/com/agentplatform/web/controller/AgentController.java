package com.agentplatform.web.controller;

import com.agentplatform.common.dto.ApiResponse;
import com.agentplatform.persistence.entity.AgentEntity;
import com.agentplatform.runtime.service.AgentDefinitionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Agent 配置管理接口。
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    @Resource
    private AgentDefinitionService agentDefinitionService;

    @PostMapping("/list")
    public ApiResponse<List<AgentEntity>> list(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(agentDefinitionService.listOrCreateDefault(parseLong(body.get("workspaceId"))));
    }

    @PostMapping("/create")
    public ApiResponse<AgentEntity> create(@RequestBody Map<String, Object> body) {
        AgentEntity entity = agentDefinitionService.create(
                parseLong(body.get("workspaceId")),
                text(body.get("name")),
                text(body.get("description")),
                text(body.get("systemPrompt")),
                text(body.get("skillsJson")),
                text(body.get("mcpServicesJson")),
                parseInt(body.get("maxIterations")),
                parseInt(body.get("timeoutSeconds")),
                parseLong(body.get("modelConfigId"))
        );
        return ApiResponse.success(entity);
    }

    @PostMapping("/update")
    public ApiResponse<AgentEntity> update(@RequestBody Map<String, Object> body) {
        AgentEntity entity = agentDefinitionService.update(
                parseLong(body.get("id")),
                text(body.get("name")),
                text(body.get("description")),
                text(body.get("systemPrompt")),
                text(body.get("skillsJson")),
                text(body.get("mcpServicesJson")),
                parseInt(body.get("maxIterations")),
                parseInt(body.get("timeoutSeconds")),
                parseLong(body.get("modelConfigId")),
                text(body.get("status"))
        );
        return ApiResponse.success(entity);
    }

    private Long parseLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer parseInt(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
