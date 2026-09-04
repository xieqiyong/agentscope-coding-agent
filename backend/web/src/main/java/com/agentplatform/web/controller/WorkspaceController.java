package com.agentplatform.web.controller;

import com.agentplatform.common.dto.ApiResponse;
import com.agentplatform.persistence.entity.WorkspaceEntity;
import com.agentplatform.workspace.service.WorkspaceService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 工作区管理接口。
 */
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    @Resource
    private com.agentplatform.runtime.service.AgentDefinitionService agentDefinitionService;


    @Resource
    private WorkspaceService workspaceService;

    @GetMapping
    public ApiResponse<List<WorkspaceEntity>> list() {
        return ApiResponse.success(workspaceService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkspaceEntity> getById(@PathVariable Long id) {
        return ApiResponse.success(workspaceService.getById(id));
    }

    @PostMapping
    public ApiResponse<WorkspaceEntity> register(@RequestBody Map<String, String> body) {
        WorkspaceEntity entity = workspaceService.register(
                body.get("name"),
                body.get("rootPath"),
                body.get("description"),
                body.get("ownerId")
        );
        // 注册新工作区时把当前工作区的智能体配置克隆过来，避免用户换目录后智能体“消失”
        if (entity != null && StringUtils.hasText(body.get("fromWorkspaceId"))) {
            try {
                agentDefinitionService.cloneWorkspaceAgents(parseLong(body.get("fromWorkspaceId")), entity.getId());
            } catch (Exception ignored) {
                // 克隆失败不影响注册结果；首次拉取列表时会兜底创建默认智能体
            }
        }
        return ApiResponse.success(entity);
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

    @PostMapping("/browse-directories")
    public ApiResponse<Map<String, Object>> browseDirectories(@RequestBody(required = false) Map<String, String> body) {
        String path = body != null ? body.get("path") : null;
        return ApiResponse.success(workspaceService.browseLocalDirectories(path));
    }

    @PostMapping("/{id}/update")
    public ApiResponse<WorkspaceEntity> update(@PathVariable Long id,
                                               @RequestBody Map<String, String> body) {
        WorkspaceEntity entity = workspaceService.update(
                id,
                body.get("name"),
                body.get("description")
        );
        return ApiResponse.success(entity);
    }

    @PostMapping("/{id}/delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        workspaceService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/tree")
    public ApiResponse<List<Map<String, Object>>> getFileTree(
            @PathVariable Long id,
            @RequestParam(defaultValue = "3") int maxDepth) {
        return ApiResponse.success(workspaceService.getFileTree(id, maxDepth));
    }
}
