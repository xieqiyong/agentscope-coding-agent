package com.agentplatform.web.controller;

import com.agentplatform.common.dto.ApiResponse;
import com.agentplatform.persistence.entity.WorkspaceEntity;
import com.agentplatform.workspace.service.WorkspaceService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工作区管理接口。
 */
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

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
        return ApiResponse.success(entity);
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkspaceEntity> update(@PathVariable Long id,
                                               @RequestBody Map<String, String> body) {
        WorkspaceEntity entity = workspaceService.update(
                id,
                body.get("name"),
                body.get("description")
        );
        return ApiResponse.success(entity);
    }

    @DeleteMapping("/{id}")
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
