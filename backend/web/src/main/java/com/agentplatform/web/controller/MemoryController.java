package com.agentplatform.web.controller;

import com.agentplatform.common.dto.ApiResponse;
import com.agentplatform.memory.service.MemoryApplicationService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 长期记忆管理接口。
 * 新增接口统一使用 POST，便于后续加入审计、权限和复杂筛选条件。
 */
@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    @Resource
    private MemoryApplicationService memoryApplicationService;

    @PostMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(memoryApplicationService.list(
                parseLong(body.get("workspaceId")),
                text(body, "userId"),
                text(body, "status")
        ));
    }

    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(memoryApplicationService.create(
                parseLong(body.get("workspaceId")),
                parseLong(body.get("agentId")),
                text(body, "userId"),
                firstText(text(body, "type"), text(body, "memoryType")),
                text(body, "content"),
                text(body, "normalizedKey")
        ));
    }

    @PostMapping("/approve")
    public ApiResponse<Map<String, Object>> approve(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(memoryApplicationService.approve(
                parseLong(body.get("id")),
                text(body, "userId")
        ));
    }

    @PostMapping("/reject")
    public ApiResponse<Map<String, Object>> reject(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(memoryApplicationService.reject(
                parseLong(body.get("id")),
                text(body, "userId")
        ));
    }

    @PostMapping("/disable")
    public ApiResponse<Map<String, Object>> disable(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(memoryApplicationService.disable(
                parseLong(body.get("id")),
                text(body, "userId")
        ));
    }

    private String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private Long parseLong(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }
}
