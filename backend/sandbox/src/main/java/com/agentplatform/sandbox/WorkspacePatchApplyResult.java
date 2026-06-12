package com.agentplatform.sandbox;

import java.util.List;

/**
 * 工作区内直接应用 patch 的结果。
 * 这个结果不代表用户审核提案，只代表已经通过沙箱校验并写入当前 workspace。
 */
public record WorkspacePatchApplyResult(
        int fileCount,
        int addedLines,
        int deletedLines,
        List<AppliedFile> files
) {

    /**
     * 单个文件的变更统计。
     */
    public record AppliedFile(
            String path,
            String changeType,
            int addedLines,
            int deletedLines
    ) {
    }
}
