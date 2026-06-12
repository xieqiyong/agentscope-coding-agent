package com.agentplatform.sandbox;

import java.util.List;

/**
 * Patch 应用结果，用于告诉前端和审计链路哪些文件已经真正写入磁盘。
 */
public record PatchApplyResult(
        Long patchId,
        String status,
        int appliedFileCount,
        List<PatchAppliedFile> files
) {

    /**
     * 单个文件的应用结果。
     */
    public record PatchAppliedFile(
            String path,
            String changeType,
            String oldContentHash,
            String newContentHash
    ) {
    }
}