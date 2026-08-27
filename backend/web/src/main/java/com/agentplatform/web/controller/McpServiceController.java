package com.agentplatform.web.controller;

import com.agentplatform.common.dto.ApiResponse;
import com.agentplatform.persistence.entity.McpServiceEntity;
import com.agentplatform.runtime.service.McpServiceService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务管理接口。
 */
@RestController
@RequestMapping("/api/mcp-services")
public class McpServiceController {

    @Resource
    private McpServiceService mcpServiceService;

    @PostMapping("/list")
    public ApiResponse<List<McpServiceEntity>> list(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(mcpServiceService.list());
    }

    @PostMapping("/save")
    public ApiResponse<McpServiceEntity> save(@RequestBody Map<String, Object> body) {
        McpServiceEntity entity = mcpServiceService.save(
                parseLong(body.get("id")),
                text(body.get("name")),
                text(body.get("description")),
                text(body.get("transportType")),
                text(body.get("endpoint")),
                body.get("enabled") == null ? null : Boolean.parseBoolean(String.valueOf(body.get("enabled")))
        );
        return ApiResponse.success(entity);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody Map<String, Object> body) {
        mcpServiceService.delete(parseLong(body.get("id")));
        return ApiResponse.success(null);
    }

    private Long parseLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
