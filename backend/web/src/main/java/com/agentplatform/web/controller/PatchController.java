package com.agentplatform.web.controller;

import com.agentplatform.common.dto.ApiResponse;
import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.PatchEntity;
import com.agentplatform.persistence.entity.PatchFileEntity;
import com.agentplatform.persistence.repository.PatchFileRepository;
import com.agentplatform.persistence.repository.PatchRepository;
import com.agentplatform.sandbox.PatchApplyResult;
import com.agentplatform.sandbox.PatchApplyService;
import jakarta.annotation.Resource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Patch 审查接口。
 * 当前阶段只负责展示和确认修改提案，不直接把 diff 写入工作区文件。
 */
@RestController
@RequestMapping("/api/patches")
public class PatchController {

    @Resource
    private PatchRepository patchRepository;

    @Resource
    private PatchFileRepository patchFileRepository;

    @Resource
    private PatchApplyService patchApplyService;

    /**
     * 确认并应用 patch。
     * 中文注释：真正写文件前会重新经过沙箱路径校验和 diff 上下文匹配。
     */
    @PostMapping("/{id}/apply")
    @Transactional
    public ApiResponse<Map<String, Object>> confirm(@PathVariable Long id) {
        PatchApplyResult applyResult = patchApplyService.apply(id);
        PatchEntity patch = getPatch(id);
        Map<String, Object> detail = toDetail(patch);
        detail.put("applyResult", applyResult);
        return ApiResponse.success(detail);
    }

    private PatchEntity getPatch(Long id) {
        return patchRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Patch 不存在"));
    }

    private Map<String, Object> toDetail(PatchEntity patch) {
        List<PatchFileEntity> patchFiles = patchFileRepository.findByPatchIdOrderByIdAsc(patch.getId());
        Map<String, DiffStat> stats = countDiffStats(patch.getDiffText());

        List<Map<String, Object>> files = new ArrayList<>();
        for (PatchFileEntity patchFile : patchFiles) {
            String path = patchFile.getFilePath();
            DiffStat stat = stats.getOrDefault(path, new DiffStat());
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("path", path);
            file.put("filePath", path);
            file.put("changeType", normalizeChangeType(patchFile.getChangeType()));
            file.put("additions", stat.additions);
            file.put("deletions", stat.deletions);
            files.add(file);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", patch.getId());
        detail.put("runId", patch.getRunId());
        detail.put("workspaceId", patch.getWorkspaceId());
        detail.put("title", patch.getTitle());
        detail.put("summary", patch.getSummary());
        detail.put("status", patch.getStatus());
        detail.put("diff", patch.getDiffText());
        detail.put("diffText", patch.getDiffText());
        detail.put("files", files);
        detail.put("createdAt", patch.getCreatedAt());
        detail.put("updatedAt", patch.getUpdatedAt());
        detail.put("appliedAt", patch.getAppliedAt());
        return detail;
    }

    private Map<String, DiffStat> countDiffStats(String diffText) {
        Map<String, DiffStat> stats = new LinkedHashMap<>();
        if (diffText == null || diffText.isBlank()) {
            return stats;
        }

        String currentPath = null;
        for (String line : diffText.split("\\R")) {
            if (line.startsWith("+++ b/")) {
                currentPath = line.substring(6).trim();
                stats.putIfAbsent(currentPath, new DiffStat());
                continue;
            }
            if (line.startsWith("--- a/") && currentPath == null) {
                currentPath = line.substring(6).trim();
                stats.putIfAbsent(currentPath, new DiffStat());
                continue;
            }
            if (currentPath == null || !stats.containsKey(currentPath)) {
                continue;
            }
            DiffStat stat = stats.get(currentPath);
            if (line.startsWith("+") && !line.startsWith("+++")) {
                stat.additions++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                stat.deletions++;
            }
        }
        return stats;
    }

    private String normalizeChangeType(String changeType) {
        if (changeType == null) {
            return "modified";
        }
        String upper = changeType.toUpperCase();
        if (upper.equals("ADD") || upper.equals("ADDED") || upper.equals("CREATE")) {
            return "added";
        }
        if (upper.equals("DELETE") || upper.equals("DELETED") || upper.equals("REMOVE")) {
            return "deleted";
        }
        return "modified";
    }

    private static class DiffStat {
        private int additions;
        private int deletions;
    }
}
