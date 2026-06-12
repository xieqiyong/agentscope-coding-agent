package com.agentplatform.runtime.tool;

import com.agentplatform.persistence.entity.PatchEntity;
import com.agentplatform.persistence.entity.PatchFileEntity;
import com.agentplatform.persistence.repository.PatchFileRepository;
import com.agentplatform.persistence.repository.PatchRepository;
import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.sandbox.ResolvedWorkspacePath;
import com.agentplatform.sandbox.SandboxPathResolver;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 暴露给 AgentScope 的工作区工具。
 * 工具内部必须走 SandboxPathResolver，不能信任模型传入的路径。
 */
public class CodingAgentWorkspaceTools {

    private static final int MAX_LIST_ITEMS = 200;
    private static final int MAX_READ_BYTES = 512 * 1024;

    private final RuntimeContext context;
    private final SandboxPathResolver sandboxPathResolver;
    private final PatchRepository patchRepository;
    private final PatchFileRepository patchFileRepository;

    public CodingAgentWorkspaceTools(RuntimeContext context,
                                     SandboxPathResolver sandboxPathResolver,
                                     PatchRepository patchRepository,
                                     PatchFileRepository patchFileRepository) {
        this.context = context;
        this.sandboxPathResolver = sandboxPathResolver;
        this.patchRepository = patchRepository;
        this.patchFileRepository = patchFileRepository;
    }

    @Tool(name = "list_files", description = "列出工作区相对目录下的文件。读取未知路径前优先调用这个工具。", readOnly = true)
    public String listFiles(
            @ToolParam(name = "path", required = false, description = "工作区相对目录路径。") String path,
            @ToolParam(name = "maxDepth", required = false, description = "最大遍历深度，最高限制为 5。") Integer maxDepth) {
        ResolvedWorkspacePath resolved = sandboxPathResolver.resolveExistingPath(rootPath(), path);
        if (!Files.isDirectory(resolved.absolutePath())) {
            return "路径不是目录：" + resolved.relativePath();
        }

        int depth = maxDepth != null && maxDepth > 0 ? Math.min(maxDepth, 5) : 2;
        StringBuilder result = new StringBuilder();
        result.append("目录列表：/").append(resolved.relativePath()).append("\n");
        try (Stream<Path> stream = Files.walk(resolved.absolutePath(), depth)) {
            List<Path> paths = stream
                    .filter(p -> !p.equals(resolved.absolutePath()))
                    .filter(p -> !isIgnoredPath(p))
                    .limit(MAX_LIST_ITEMS)
                    .toList();
            for (Path item : paths) {
                String relative = resolved.rootPath().relativize(item).toString().replace('\\', '/');
                String type = Files.isDirectory(item) ? "目录" : "文件";
                long size = Files.isRegularFile(item) ? Files.size(item) : 0;
                result.append(type)
                        .append(" /")
                        .append(relative)
                        .append(" 大小=")
                        .append(size)
                        .append("\n");
            }
            if (paths.size() >= MAX_LIST_ITEMS) {
                result.append("结果已截断，最多返回 ").append(MAX_LIST_ITEMS).append(" 项。\n");
            }
            return result.toString();
        } catch (IOException e) {
            return "列出文件失败：" + e.getMessage();
        }
    }

    @Tool(name = "read_file", description = "读取工作区内的文本文件。敏感文件和二进制文件会被拒绝。", readOnly = true)
    public String readFile(
            @ToolParam(name = "path", description = "工作区相对文件路径。") String path,
            @ToolParam(name = "maxChars", required = false, description = "最多返回字符数，最高限制为 20000。") Integer maxChars) {
        ResolvedWorkspacePath resolved = sandboxPathResolver.resolveExistingPath(rootPath(), path);
        if (!Files.isRegularFile(resolved.absolutePath())) {
            return "路径不是普通文件：" + resolved.relativePath();
        }
        if (isSensitivePath(resolved.relativePath())) {
            return "拒绝读取敏感文件：" + resolved.relativePath();
        }
        try {
            long size = Files.size(resolved.absolutePath());
            if (size > MAX_READ_BYTES) {
                return "拒绝读取过大的文件：" + resolved.relativePath() + "，大小=" + size;
            }
            byte[] bytes = Files.readAllBytes(resolved.absolutePath());
            if (looksBinary(bytes)) {
                return "拒绝读取二进制文件：" + resolved.relativePath();
            }
            int limit = maxChars != null && maxChars > 0 ? Math.min(maxChars, 20_000) : 12_000;
            String text = new String(bytes, StandardCharsets.UTF_8);
            boolean truncated = text.length() > limit;
            String body = truncated ? text.substring(0, limit) : text;
            return "文件：/" + resolved.relativePath() + "\n"
                    + "字符数：" + text.length() + "\n"
                    + "是否截断：" + (truncated ? "是" : "否") + "\n\n"
                    + body;
        } catch (IOException e) {
            return "读取文件失败：" + e.getMessage();
        }
    }

    @Tool(name = "search_code", description = "在工作区文件中搜索文本，不执行 shell 命令。", readOnly = true)
    public String searchCode(
            @ToolParam(name = "query", description = "要搜索的文本。") String query,
            @ToolParam(name = "path", required = false, description = "工作区相对搜索目录。") String path,
            @ToolParam(name = "maxResults", required = false, description = "最多返回结果数，最高限制为 50。") Integer maxResults) {
        if (!StringUtils.hasText(query)) {
            return "搜索文本不能为空";
        }
        ResolvedWorkspacePath resolved = sandboxPathResolver.resolveExistingPath(rootPath(), path);
        int limit = maxResults != null && maxResults > 0 ? Math.min(maxResults, 50) : 20;
        String normalizedQuery = query.toLowerCase();
        StringBuilder result = new StringBuilder("搜索文本：").append(query).append("\n");
        int count = 0;
        try (Stream<Path> stream = Files.walk(resolved.absolutePath(), 8)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !isIgnoredPath(p))
                    .filter(p -> !isSensitivePath(resolved.rootPath().relativize(p).toString().replace('\\', '/')))
                    .limit(1000)
                    .toList();
            for (Path file : files) {
                if (count >= limit) {
                    break;
                }
                byte[] bytes = Files.readAllBytes(file);
                if (looksBinary(bytes)) {
                    continue;
                }
                String text = new String(bytes, StandardCharsets.UTF_8);
                String[] lines = text.split("\\R", -1);
                for (int i = 0; i < lines.length && count < limit; i++) {
                    if (lines[i].toLowerCase().contains(normalizedQuery)) {
                        String relative = resolved.rootPath().relativize(file).toString().replace('\\', '/');
                        result.append("/")
                                .append(relative)
                                .append(":")
                                .append(i + 1)
                                .append(": ")
                                .append(abbreviate(lines[i].trim(), 240))
                                .append("\n");
                        count++;
                    }
                }
            }
            if (count == 0) {
                result.append("没有找到匹配结果。\n");
            }
            return result.toString();
        } catch (IOException e) {
            return "搜索代码失败：" + e.getMessage();
        }
    }

    @Tool(name = "propose_patch", description = "保存一个 unified diff 修改提案供用户审核。这个工具永远不会直接写文件。", readOnly = false)
    public String proposePatch(
            @ToolParam(name = "summary", description = "本次修改的简短说明。") String summary,
            @ToolParam(name = "unifiedDiff", description = "unified diff 文本。") String unifiedDiff) {
        if (!StringUtils.hasText(unifiedDiff)) {
            return "补丁内容 unifiedDiff 不能为空";
        }

        Set<String> files = extractPatchFiles(unifiedDiff);
        for (String file : files) {
            // 只验证路径是否仍在工作区内，不要求文件必须已经存在，因为 patch 可能新增文件。
            sandboxPathResolver.resolvePath(rootPath(), file, false);
        }

        return savePatchProposal(summary, unifiedDiff, files, "MODIFY");
    }

    @Tool(name = "propose_file_change", description = "保存单个文件的新建或整文件替换提案，适合创建 package.json、Vue 组件等大文件。不会直接写文件；多文件任务必须拆成多次调用。", readOnly = false)
    public String proposeFileChange(
            @ToolParam(name = "path", description = "工作区相对文件路径。") String path,
            @ToolParam(name = "changeType", description = "变更类型：CREATE 或 REPLACE。") String changeType,
            @ToolParam(name = "content", description = "文件完整内容。") String content,
            @ToolParam(name = "summary", required = false, description = "本次单文件变更说明。") String summary) {
        if (!StringUtils.hasText(path)) {
            return "文件路径 path 不能为空";
        }
        if (content == null) {
            content = "";
        }
        if (isSensitivePath(path)) {
            return "拒绝修改敏感文件：" + path;
        }

        ResolvedWorkspacePath resolved = sandboxPathResolver.resolvePath(rootPath(), path, false);
        String normalizedType = StringUtils.hasText(changeType) ? changeType.trim().toUpperCase() : "CREATE";
        boolean exists = Files.exists(resolved.absolutePath());
        if ("CREATE".equals(normalizedType) && exists) {
            return "文件已存在，不能使用 CREATE，请改用 REPLACE：" + resolved.relativePath();
        }
        if ("REPLACE".equals(normalizedType) && !exists) {
            return "文件不存在，不能使用 REPLACE，请改用 CREATE：" + resolved.relativePath();
        }
        if (!"CREATE".equals(normalizedType) && !"REPLACE".equals(normalizedType)) {
            return "changeType 只支持 CREATE 或 REPLACE";
        }

        try {
            String oldContent = "";
            if (exists) {
                byte[] oldBytes = Files.readAllBytes(resolved.absolutePath());
                if (looksBinary(oldBytes)) {
                    return "拒绝替换二进制文件：" + resolved.relativePath();
                }
                oldContent = new String(oldBytes, StandardCharsets.UTF_8);
            }

            String unifiedDiff = buildFullFileDiff(resolved.relativePath(), oldContent, content, "CREATE".equals(normalizedType));
            return savePatchProposal(
                    StringUtils.hasText(summary) ? summary : normalizedType + " " + resolved.relativePath(),
                    unifiedDiff,
                    Set.of(resolved.relativePath()),
                    normalizedType
            );
        } catch (IOException e) {
            return "生成文件变更提案失败：" + e.getMessage();
        }
    }

    private String savePatchProposal(String summary, String unifiedDiff, Set<String> files, String changeType) {
        PatchEntity patch = new PatchEntity();
        patch.setRunId(context.getRunId());
        patch.setWorkspaceId(context.getWorkspace().getId());
        patch.setTitle(StringUtils.hasText(summary) ? abbreviate(summary, 120) : "智能体修改提案");
        patch.setSummary(summary);
        patch.setDiffText(unifiedDiff);
        patch.setStatus("PROPOSED");
        patch = patchRepository.save(patch);

        for (String file : files) {
            PatchFileEntity patchFile = new PatchFileEntity();
            patchFile.setPatchId(patch.getId());
            patchFile.setFilePath(file);
            patchFile.setChangeType(changeType);
            patchFileRepository.save(patchFile);
        }

        String createdAtText = patch.getCreatedAt() != null
                ? DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(patch.getCreatedAt())
                : "刚刚";
        return "修改提案已保存。\n"
                + "补丁 ID：" + patch.getId() + "\n"
                + "影响文件数：" + files.size() + "\n"
                + "需要用户确认：是\n"
                + "创建时间：" + createdAtText;
    }

    private String buildFullFileDiff(String path, String oldContent, String newContent, boolean create) {
        List<String> oldLines = splitLines(oldContent);
        List<String> newLines = splitLines(newContent);
        StringBuilder diff = new StringBuilder();
        diff.append("--- ").append(create ? "/dev/null" : "a/" + path).append("\n");
        diff.append("+++ b/").append(path).append("\n");
        diff.append("@@ -")
                .append(create ? 0 : 1)
                .append(',')
                .append(create ? 0 : oldLines.size())
                .append(" +")
                .append(newLines.isEmpty() ? 0 : 1)
                .append(',')
                .append(newLines.size())
                .append(" @@\n");
        if (!create) {
            for (String line : oldLines) {
                diff.append('-').append(line).append("\n");
            }
        }
        for (String line : newLines) {
            diff.append('+').append(line).append("\n");
        }
        return diff.toString();
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String[] lines = text.split("\\R", -1);
        if (lines.length > 0 && lines[lines.length - 1].isEmpty()) {
            return List.of(lines).subList(0, lines.length - 1);
        }
        return List.of(lines);
    }
    private String rootPath() {
        return context.getWorkspace().getRootPath();
    }

    private Set<String> extractPatchFiles(String diff) {
        Set<String> files = new LinkedHashSet<>();
        String[] lines = diff.split("\\R");
        for (String line : lines) {
            if (line.startsWith("+++ b/") || line.startsWith("--- a/")) {
                String file = line.substring(6).trim();
                if (StringUtils.hasText(file) && !"/dev/null".equals(file)) {
                    files.add(file);
                }
            }
        }
        return files;
    }

    private boolean isIgnoredPath(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/.git/")
                || normalized.contains("/node_modules/")
                || normalized.contains("/target/")
                || normalized.contains("/dist/")
                || normalized.contains("/build/");
    }

    private boolean isSensitivePath(String relativePath) {
        String lower = relativePath.toLowerCase();
        return lower.endsWith(".env")
                || lower.contains("/.env")
                || lower.endsWith(".pem")
                || lower.endsWith(".key")
                || lower.contains("id_rsa")
                || lower.contains("credentials")
                || lower.contains("secret")
                || lower.contains("token");
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

    private String abbreviate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }
}

