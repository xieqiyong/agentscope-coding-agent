package com.agentplatform.sandbox;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.PatchEntity;
import com.agentplatform.persistence.entity.PatchFileEntity;
import com.agentplatform.persistence.entity.WorkspaceEntity;
import com.agentplatform.persistence.repository.PatchFileRepository;
import com.agentplatform.persistence.repository.PatchRepository;
import com.agentplatform.persistence.repository.WorkspaceRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 受沙箱治理的 Patch 应用服务。
 * 模型只能生成 patch 提案，真正写入磁盘必须走这里的路径校验和内容匹配。
 */
@Service
public class PatchApplyService {

    private static final Pattern HUNK_HEADER = Pattern.compile("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*");

    @Resource
    private PatchRepository patchRepository;

    @Resource
    private PatchFileRepository patchFileRepository;

    @Resource
    private WorkspaceRepository workspaceRepository;

    @Resource
    private SandboxPathResolver sandboxPathResolver;

    /**
     * 应用一个已经由用户确认的 patch。
     */
    @Transactional
    public PatchApplyResult apply(Long patchId) {
        PatchEntity patch = patchRepository.findById(patchId)
                .orElseThrow(() -> new BusinessException(404, "Patch 不存在"));
        if ("APPLIED".equalsIgnoreCase(patch.getStatus())) {
            return alreadyApplied(patch);
        }
        if (!"PROPOSED".equalsIgnoreCase(patch.getStatus()) && !"APPROVED".equalsIgnoreCase(patch.getStatus())) {
            throw new BusinessException(409, "当前 Patch 状态不允许应用：" + patch.getStatus());
        }

        WorkspaceEntity workspace = workspaceRepository.findById(patch.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(404, "工作区不存在"));
        List<FilePatch> filePatches = parseUnifiedDiff(patch.getDiffText());
        if (filePatches.isEmpty()) {
            throw new BusinessException(400, "Patch 内容为空或不是 unified diff 格式");
        }

        // 中文注释：先完整计算所有文件的新内容，全部成功后再写磁盘，避免上下文不匹配时写一半。
        List<PlannedChange> plannedChanges = new ArrayList<>();
        for (FilePatch filePatch : filePatches) {
            plannedChanges.add(planChange(workspace.getRootPath(), filePatch));
        }

        writeChanges(plannedChanges);
        List<PatchApplyResult.PatchAppliedFile> appliedFiles = updatePatchFiles(patch.getId(), plannedChanges);

        patch.setStatus("APPLIED");
        patch.setAppliedAt(LocalDateTime.now());
        patchRepository.save(patch);

        return new PatchApplyResult(patch.getId(), patch.getStatus(), appliedFiles.size(), appliedFiles);
    }

    /**
     * 直接在当前 workspace 内应用 unified diff。
     * 中文注释：这个入口给 coding agent 的直接写文件工具使用，不创建审核提案。
     */
    public WorkspacePatchApplyResult applyUnifiedDiff(String workspaceRoot, String diffText) {
        List<FilePatch> filePatches = parseUnifiedDiff(diffText);
        if (filePatches.isEmpty()) {
            throw new BusinessException(400, "Patch 内容为空或不是 unified diff 格式");
        }

        List<PlannedChange> plannedChanges = new ArrayList<>();
        for (FilePatch filePatch : filePatches) {
            plannedChanges.add(planChange(workspaceRoot, filePatch));
        }

        writeChanges(plannedChanges);
        return toWorkspaceResult(plannedChanges);
    }

    private PatchApplyResult alreadyApplied(PatchEntity patch) {
        List<PatchApplyResult.PatchAppliedFile> files = patchFileRepository.findByPatchIdOrderByIdAsc(patch.getId())
                .stream()
                .map(file -> new PatchApplyResult.PatchAppliedFile(
                        file.getFilePath(),
                        file.getChangeType(),
                        file.getOldContentHash(),
                        file.getNewContentHash()))
                .toList();
        return new PatchApplyResult(patch.getId(), patch.getStatus(), files.size(), files);
    }

    private WorkspacePatchApplyResult toWorkspaceResult(List<PlannedChange> changes) {
        List<WorkspacePatchApplyResult.AppliedFile> files = new ArrayList<>();
        int totalAdded = 0;
        int totalDeleted = 0;
        for (PlannedChange change : changes) {
            LineChangeStats stats = calculateLineChangeStats(
                    bytesToLines(change.oldBytes()),
                    change.newBytes() == null ? List.of() : bytesToLines(change.newBytes())
            );
            totalAdded += stats.addedLines();
            totalDeleted += stats.deletedLines();
            files.add(new WorkspacePatchApplyResult.AppliedFile(
                    change.relativePath(),
                    change.changeType(),
                    stats.addedLines(),
                    stats.deletedLines()
            ));
        }
        return new WorkspacePatchApplyResult(files.size(), totalAdded, totalDeleted, files);
    }

    private PlannedChange planChange(String workspaceRoot, FilePatch filePatch) {
        String targetPath = filePatch.targetPath();
        if (!StringUtils.hasText(targetPath)) {
            throw new BusinessException(400, "Patch 缺少目标文件路径");
        }
        ResolvedWorkspacePath resolved = sandboxPathResolver.resolvePath(workspaceRoot, targetPath, false);
        Path absolutePath = resolved.absolutePath();
        boolean oldExists = Files.exists(absolutePath);
        byte[] oldBytes = readBytesIfExists(absolutePath);
        List<String> oldLines = bytesToLines(oldBytes);

        if (!oldExists && !filePatch.isCreate()) {
            throw new BusinessException(409, "Patch 目标文件不存在：" + targetPath);
        }

        List<String> newLines = applyHunks(targetPath, oldLines, filePatch.hunks());
        byte[] newBytes = filePatch.isDelete() ? null : String.join(System.lineSeparator(), newLines).getBytes(StandardCharsets.UTF_8);
        String changeType = filePatch.isCreate() ? "CREATE" : filePatch.isDelete() ? "DELETE" : "MODIFY";

        return new PlannedChange(
                resolved.relativePath(),
                absolutePath,
                changeType,
                oldExists,
                oldBytes,
                newBytes,
                sha256(oldBytes),
                newBytes == null ? null : sha256(newBytes)
        );
    }

    private List<String> applyHunks(String path, List<String> oldLines, List<Hunk> hunks) {
        List<String> result = new ArrayList<>();
        int cursor = 0;
        for (Hunk hunk : hunks) {
            int hunkStart = Math.max(0, hunk.oldStart() - 1);
            if (hunkStart < cursor || hunkStart > oldLines.size()) {
                throw new BusinessException(409, "Patch hunk 位置不匹配：" + path);
            }
            while (cursor < hunkStart) {
                result.add(oldLines.get(cursor++));
            }
            for (PatchLine line : hunk.lines()) {
                if (line.kind() == ' ') {
                    assertLineMatches(path, oldLines, cursor, line.content());
                    result.add(oldLines.get(cursor++));
                } else if (line.kind() == '-') {
                    assertLineMatches(path, oldLines, cursor, line.content());
                    cursor++;
                } else if (line.kind() == '+') {
                    result.add(line.content());
                }
            }
        }
        while (cursor < oldLines.size()) {
            result.add(oldLines.get(cursor++));
        }
        return result;
    }

    private void assertLineMatches(String path, List<String> oldLines, int cursor, String expected) {
        if (cursor >= oldLines.size()) {
            throw new BusinessException(409, "Patch 上下文超出文件末尾：" + path);
        }
        String actual = oldLines.get(cursor);
        if (!actual.equals(expected)) {
            throw new BusinessException(409, "Patch 上下文不匹配：" + path + "，行号 " + (cursor + 1));
        }
    }

    private void writeChanges(List<PlannedChange> changes) {
        List<PlannedChange> written = new ArrayList<>();
        try {
            for (PlannedChange change : changes) {
                if (change.newBytes() == null) {
                    Files.deleteIfExists(change.absolutePath());
                } else {
                    Path parent = change.absolutePath().getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.write(change.absolutePath(), change.newBytes());
                }
                written.add(change);
            }
        } catch (IOException e) {
            rollbackWrittenChanges(written);
            throw new BusinessException(500, "Patch 写入失败，已尝试回滚：" + e.getMessage());
        }
    }

    private void rollbackWrittenChanges(List<PlannedChange> written) {
        for (int i = written.size() - 1; i >= 0; i--) {
            PlannedChange change = written.get(i);
            try {
                if (change.oldExists()) {
                    Files.write(change.absolutePath(), change.oldBytes());
                } else {
                    Files.deleteIfExists(change.absolutePath());
                }
            } catch (IOException ignored) {
                // 中文注释：回滚失败只能交给上层审计和人工处理，不能吞掉原始写入异常。
            }
        }
    }

    private List<PatchApplyResult.PatchAppliedFile> updatePatchFiles(Long patchId, List<PlannedChange> changes) {
        Map<String, PatchFileEntity> fileMap = new HashMap<>();
        for (PatchFileEntity file : patchFileRepository.findByPatchIdOrderByIdAsc(patchId)) {
            fileMap.put(normalizePath(file.getFilePath()), file);
        }

        List<PatchApplyResult.PatchAppliedFile> result = new ArrayList<>();
        for (PlannedChange change : changes) {
            PatchFileEntity file = fileMap.computeIfAbsent(change.relativePath(), ignored -> {
                PatchFileEntity created = new PatchFileEntity();
                created.setPatchId(patchId);
                created.setFilePath(change.relativePath());
                return created;
            });
            file.setChangeType(change.changeType());
            file.setOldContentHash(change.oldContentHash());
            file.setNewContentHash(change.newContentHash());
            patchFileRepository.save(file);
            result.add(new PatchApplyResult.PatchAppliedFile(
                    file.getFilePath(),
                    file.getChangeType(),
                    file.getOldContentHash(),
                    file.getNewContentHash()
            ));
        }
        return result;
    }

    private List<FilePatch> parseUnifiedDiff(String diffText) {
        if (!StringUtils.hasText(diffText)) {
            return List.of();
        }
        String[] lines = diffText.split("\\R", -1);
        List<FilePatch> patches = new ArrayList<>();
        FilePatch currentFile = null;
        Hunk currentHunk = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("diff --git ")) {
                addIfComplete(patches, currentFile);
                currentFile = null;
                currentHunk = null;
                continue;
            }
            if (line.startsWith("--- ") && i + 1 < lines.length && lines[i + 1].startsWith("+++ ")) {
                if (currentFile != null) {
                    addIfComplete(patches, currentFile);
                }
                currentFile = new FilePatch(parseDiffPath(line.substring(4)), null, new ArrayList<>());
                currentHunk = null;
                continue;
            }
            if (currentHunk != null && isPatchBodyLine(line)) {
                if (line.startsWith("\\ No newline at end of file")) {
                    continue;
                }
                currentHunk.lines().add(new PatchLine(line.charAt(0), line.substring(1)));
                continue;
            }
            if (line.startsWith("--- ")) {
                if (currentFile != null && currentFile.newPath() != null) {
                    addIfComplete(patches, currentFile);
                }
                currentFile = new FilePatch(parseDiffPath(line.substring(4)), null, new ArrayList<>());
                currentHunk = null;
                continue;
            }
            if (line.startsWith("+++ ")) {
                if (currentFile == null) {
                    throw new BusinessException(400, "Patch 缺少 --- 文件头");
                }
                currentFile = currentFile.withNewPath(parseDiffPath(line.substring(4)));
                currentHunk = null;
                continue;
            }
            if (line.startsWith("@@ ")) {
                if (currentFile == null || currentFile.newPath() == null) {
                    throw new BusinessException(400, "Patch hunk 缺少文件头");
                }
                currentHunk = parseHunk(line);
                currentFile.hunks().add(currentHunk);
            }
        }
        addIfComplete(patches, currentFile);
        return patches;
    }

    private boolean isPatchBodyLine(String line) {
        return line.startsWith(" ") || line.startsWith("+") || line.startsWith("-") || line.startsWith("\\ No newline at end of file");
    }

    private Hunk parseHunk(String line) {
        Matcher matcher = HUNK_HEADER.matcher(line);
        if (!matcher.matches()) {
            throw new BusinessException(400, "Patch hunk 头格式不正确：" + line);
        }
        return new Hunk(Integer.parseInt(matcher.group(1)), new ArrayList<>());
    }

    private String parseDiffPath(String rawPath) {
        String token = rawPath.trim();
        int tabIndex = token.indexOf('\t');
        if (tabIndex >= 0) {
            token = token.substring(0, tabIndex);
        }
        if ("/dev/null".equals(token)) {
            return null;
        }
        if (token.startsWith("a/") || token.startsWith("b/")) {
            token = token.substring(2);
        }
        return normalizePath(token);
    }

    private void addIfComplete(List<FilePatch> patches, FilePatch filePatch) {
        if (filePatch == null) {
            return;
        }
        if (!StringUtils.hasText(filePatch.targetPath()) || filePatch.hunks().isEmpty()) {
            return;
        }
        patches.add(filePatch);
    }

    private byte[] readBytesIfExists(Path path) {
        if (!Files.exists(path)) {
            return new byte[0];
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (looksBinary(bytes)) {
                throw new BusinessException(415, "拒绝修改二进制文件：" + path.getFileName());
            }
            return bytes;
        } catch (IOException e) {
            throw new BusinessException(500, "读取待修改文件失败：" + e.getMessage());
        }
    }

    private List<String> bytesToLines(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new ArrayList<>();
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        String[] split = text.split("\\R", -1);
        List<String> lines = new ArrayList<>(List.of(split));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private boolean looksBinary(byte[] bytes) {
        int max = Math.min(bytes.length, 4096);
        for (int i = 0; i < max; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String sha256(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException(500, "计算文件 hash 失败");
        }
    }

    private LineChangeStats calculateLineChangeStats(List<String> oldLines, List<String> newLines) {
        if (oldLines.isEmpty()) {
            return new LineChangeStats(newLines.size(), 0);
        }
        if (newLines.isEmpty()) {
            return new LineChangeStats(0, oldLines.size());
        }
        if ((long) oldLines.size() * (long) newLines.size() > 250_000L) {
            // 中文注释：超大文件不做昂贵 LCS，按整文件替换统计，避免工具调用卡死。
            return new LineChangeStats(newLines.size(), oldLines.size());
        }

        int[][] lcs = new int[oldLines.size() + 1][newLines.size() + 1];
        for (int i = oldLines.size() - 1; i >= 0; i--) {
            for (int j = newLines.size() - 1; j >= 0; j--) {
                if (oldLines.get(i).equals(newLines.get(j))) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }
        int unchanged = lcs[0][0];
        return new LineChangeStats(newLines.size() - unchanged, oldLines.size() - unchanged);
    }

    private record FilePatch(String oldPath, String newPath, List<Hunk> hunks) {
        FilePatch withNewPath(String value) {
            return new FilePatch(oldPath, value, hunks);
        }

        String targetPath() {
            return newPath != null ? newPath : oldPath;
        }

        boolean isCreate() {
            return oldPath == null && newPath != null;
        }

        boolean isDelete() {
            return oldPath != null && newPath == null;
        }
    }

    private record Hunk(int oldStart, List<PatchLine> lines) {
    }

    private record PatchLine(char kind, String content) {
    }

    private record PlannedChange(
            String relativePath,
            Path absolutePath,
            String changeType,
            boolean oldExists,
            byte[] oldBytes,
            byte[] newBytes,
            String oldContentHash,
            String newContentHash
    ) {
    }

    private record LineChangeStats(int addedLines, int deletedLines) {
    }
}
