package com.agentplatform.web.controller;

import com.agentplatform.common.dto.ApiResponse;
import com.agentplatform.persistence.entity.ToolDefinitionEntity;
import com.agentplatform.runtime.service.ToolDefinitionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 平台能力定义查询接口。
 */
@RestController
@RequestMapping("/api/tools")
public class ToolDefinitionController {

    @Resource
    private ToolDefinitionService toolDefinitionService;

    @PostMapping("/list")
    public ApiResponse<List<ToolDefinitionEntity>> list(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success(toolDefinitionService.listEnabled(parseToolTypes(body)));
    }

    private List<String> parseToolTypes(Map<String, Object> body) {
        if (body == null || body.get("toolTypes") == null) {
            return List.of();
        }
        Object value = body.get("toolTypes");
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return List.of(String.valueOf(value));
    }
}
