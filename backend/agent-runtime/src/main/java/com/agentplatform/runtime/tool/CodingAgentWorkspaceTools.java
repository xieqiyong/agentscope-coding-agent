package com.agentplatform.runtime.tool;

import com.agentplatform.persistence.entity.PatchEntity;
import com.agentplatform.persistence.entity.PatchFileEntity;
import com.agentplatform.persistence.repository.PatchFileRepository;
import com.agentplatform.persistence.repository.PatchRepository;
import com.agentplatform.runtime.model.RuntimeContext;
import com.agentplatform.sandbox.PatchApplyService;
import com.agentplatform.sandbox.ResolvedWorkspacePath;
import com.agentplatform.sandbox.SandboxPathResolver;
import com.agentplatform.sandbox.WorkspacePatchApplyResult;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final PatchApplyService patchApplyService;

    public CodingAgentWorkspaceTools(RuntimeContext context,
                                     SandboxPathResolver sandboxPathResolver,
                                     PatchRepository patchRepository,
                                     PatchFileRepository patchFileRepository,
                                     PatchApplyService patchApplyService) {
        this.context = context;
        this.sandboxPathResolver = sandboxPathResolver;
        this.patchRepository = patchRepository;
        this.patchFileRepository = patchFileRepository;
        this.patchApplyService = patchApplyService;
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

    @Tool(name = "write_file", description = "在当前工作区内直接创建或整文件替换文本文件。只允许写入当前 workspace 内的非敏感文本文件。", readOnly = false)
    public String writeFile(
            @ToolParam(name = "path", description = "工作区相对文件路径。") String path,
            @ToolParam(name = "content", description = "要写入的完整文件内容。") String content,
            @ToolParam(name = "writeMode", required = false, description = "写入模式：CREATE、REPLACE 或 UPSERT，默认 UPSERT。") String writeMode) {
        if (!StringUtils.hasText(path)) {
            return "文件路径 path 不能为空";
        }
        if (content == null) {
            content = "";
        }
        if (isSensitivePath(path)) {
            return "拒绝直接修改敏感文件：" + path + "。如确需修改，请使用 propose_file_change 创建审核提案。";
        }

        ResolvedWorkspacePath resolved = sandboxPathResolver.resolvePath(rootPath(), path, false);
        String mode = StringUtils.hasText(writeMode) ? writeMode.trim().toUpperCase() : "UPSERT";
        boolean exists = Files.exists(resolved.absolutePath());
        if ("CREATE".equals(mode) && exists) {
            return "文件已存在，不能使用 CREATE：" + resolved.relativePath();
        }
        if ("REPLACE".equals(mode) && !exists) {
            return "文件不存在，不能使用 REPLACE：" + resolved.relativePath();
        }
        if (!"CREATE".equals(mode) && !"REPLACE".equals(mode) && !"UPSERT".equals(mode)) {
            return "writeMode 只支持 CREATE、REPLACE 或 UPSERT";
        }

        try {
            String oldContent = "";
            if (exists) {
                if (!Files.isRegularFile(resolved.absolutePath())) {
                    return "拒绝写入非普通文件：" + resolved.relativePath();
                }
                byte[] oldBytes = Files.readAllBytes(resolved.absolutePath());
                if (looksBinary(oldBytes)) {
                    return "拒绝修改二进制文件：" + resolved.relativePath();
                }
                oldContent = new String(oldBytes, StandardCharsets.UTF_8);
            }

            Path parent = resolved.absolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(resolved.absolutePath(), content.getBytes(StandardCharsets.UTF_8));

            LineChangeStats stats = calculateLineChangeStats(splitLines(oldContent), splitLines(content));
            String changeType = exists ? "MODIFY" : "CREATE";
            return "文件已直接写入当前工作区。\n"
                    + "已编辑文件：1\n"
                    + "总变更：+" + stats.addedLines() + " / -" + stats.deletedLines() + "\n"
                    + "- " + resolved.relativePath() + " (" + changeType + "): +"
                    + stats.addedLines() + " / -" + stats.deletedLines();
        } catch (IOException e) {
            return "写入文件失败：" + e.getMessage();
        }
    }

    @Tool(name = "apply_patch", description = "在当前工作区内直接应用 unified diff。只允许修改当前 workspace 内的非敏感文本文件。", readOnly = false)
    public String applyPatch(
            @ToolParam(name = "unifiedDiff", description = "要直接应用的 unified diff 文本。") String unifiedDiff) {
        if (!StringUtils.hasText(unifiedDiff)) {
            return "patch 内容 unifiedDiff 不能为空";
        }
        try {
            WorkspacePatchApplyResult result = patchApplyService.applyUnifiedDiff(rootPath(), unifiedDiff);
            return formatWorkspacePatchResult(result);
        } catch (Exception e) {
            return "直接应用 patch 失败：" + e.getMessage();
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

    @Tool(name = "LS", description = "List files under the current workspace. Claude Code style alias for list_files.", readOnly = true)
    public String LS(
            @ToolParam(name = "path", required = false, description = "Workspace relative path or absolute path inside workspace.") String path,
            @ToolParam(name = "maxDepth", required = false, description = "Maximum traversal depth, capped at 5.") Integer maxDepth) {
        return listFiles(path, maxDepth);
    }

    @Tool(name = "Read", description = "Read a text file inside the current workspace, optionally by line window.", readOnly = true)
    public String Read(
            @ToolParam(name = "file_path", description = "Workspace relative path or absolute path inside workspace.") String filePath,
            @ToolParam(name = "offset", required = false, description = "Start line number, default 1.") Integer offset,
            @ToolParam(name = "limit", required = false, description = "Number of lines to read, default 2000, capped at 2000.") Integer limit) {
        return readFileLines(filePath, offset, limit);
    }

    @Tool(name = "Glob", description = "Find files by glob pattern inside the current workspace, for example **/*.java.", readOnly = true)
    public String Glob(
            @ToolParam(name = "pattern", description = "Glob file pattern.") String pattern,
            @ToolParam(name = "path", required = false, description = "Search directory, default workspace root.") String path) {
        if (!StringUtils.hasText(pattern)) {
            return "pattern cannot be empty";
        }
        ResolvedWorkspacePath base = sandboxPathResolver.resolveExistingPath(rootPath(), path);
        if (!Files.isDirectory(base.absolutePath())) {
            return "Search path is not a directory: " + base.relativePath();
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizeGlob(pattern));
        List<Path> matched = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(base.absolutePath(), 12)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !isIgnoredPath(p))
                    .filter(p -> matcher.matches(base.rootPath().relativize(p)))
                    .sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed())
                    .limit(200)
                    .forEach(matched::add);
        } catch (IOException e) {
            return "Glob failed: " + e.getMessage();
        }

        if (matched.isEmpty()) {
            return "No files matched: " + pattern;
        }
        StringBuilder result = new StringBuilder("Matched files: ").append(pattern).append("\n");
        for (Path file : matched) {
            result.append(base.rootPath().relativize(file).toString().replace('\\', '/')).append("\n");
        }
        return result.toString();
    }

    @Tool(name = "Grep", description = "Search text by regular expression inside the current workspace.", readOnly = true)
    public String Grep(
            @ToolParam(name = "pattern", description = "Regular expression pattern.") String pattern,
            @ToolParam(name = "path", required = false, description = "Search directory, default workspace root.") String path,
            @ToolParam(name = "glob", required = false, description = "File filter glob, for example **/*.java.") String glob,
            @ToolParam(name = "output_mode", required = false, description = "content, files_with_matches, or count. Default content.") String outputMode,
            @ToolParam(name = "ignore_case", required = false, description = "Ignore case.") Boolean ignoreCase,
            @ToolParam(name = "head_limit", required = false, description = "Maximum number of results, default 50, capped at 250.") Integer headLimit) {
        if (!StringUtils.hasText(pattern)) {
            return "pattern cannot be empty";
        }

        ResolvedWorkspacePath base = sandboxPathResolver.resolveExistingPath(rootPath(), path);
        if (!Files.isDirectory(base.absolutePath())) {
            return "Search path is not a directory: " + base.relativePath();
        }

        Pattern compiled;
        try {
            compiled = Pattern.compile(pattern, Boolean.TRUE.equals(ignoreCase) ? Pattern.CASE_INSENSITIVE : 0);
        } catch (Exception e) {
            return "Invalid regex pattern: " + e.getMessage();
        }

        String mode = StringUtils.hasText(outputMode) ? outputMode : "content";
        int limit = headLimit != null && headLimit > 0 ? Math.min(headLimit, 250) : 50;
        PathMatcher matcher = StringUtils.hasText(glob)
                ? FileSystems.getDefault().getPathMatcher("glob:" + normalizeGlob(glob))
                : null;
        LinkedHashSet<String> filesWithMatches = new LinkedHashSet<>();
        StringBuilder content = new StringBuilder();
        int count = 0;

        try (Stream<Path> stream = Files.walk(base.absolutePath(), 12)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(p -> !isIgnoredPath(p))
                    .filter(p -> matcher == null || matcher.matches(base.rootPath().relativize(p)))
                    .limit(2000)
                    .toList();
            for (Path file : files) {
                if (count >= limit) {
                    break;
                }
                byte[] bytes = Files.readAllBytes(file);
                if (looksBinary(bytes)) {
                    continue;
                }
                String relative = base.rootPath().relativize(file).toString().replace('\\', '/');
                String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\\R", -1);
                for (int i = 0; i < lines.length && count < limit; i++) {
                    Matcher lineMatcher = compiled.matcher(lines[i]);
                    if (!lineMatcher.find()) {
                        continue;
                    }
                    count++;
                    filesWithMatches.add(relative);
                    if ("content".equalsIgnoreCase(mode)) {
                        content.append(relative)
                                .append(":")
                                .append(i + 1)
                                .append(": ")
                                .append(abbreviate(lines[i].trim(), 240))
                                .append("\n");
                    }
                }
            }
        } catch (IOException e) {
            return "Grep failed: " + e.getMessage();
        }

        if ("count".equalsIgnoreCase(mode)) {
            return "Match count: " + count;
        }
        if ("files_with_matches".equalsIgnoreCase(mode)) {
            return filesWithMatches.isEmpty() ? "No matching files" : String.join("\n", filesWithMatches);
        }
        return content.length() == 0 ? "No matches" : content.toString();
    }

    @Tool(name = "Write", description = "Create or overwrite a text file inside the current workspace.", readOnly = false)
    public String Write(
            @ToolParam(name = "file_path", description = "Workspace relative path or absolute path inside workspace.") String filePath,
            @ToolParam(name = "content", description = "Full file content to write.") String content) {
        return writeFile(filePath, content, "UPSERT");
    }

    @Tool(name = "Edit", description = "Perform exact string replacement in a text file inside the current workspace.", readOnly = false)
    public String Edit(
            @ToolParam(name = "file_path", description = "Workspace relative path or absolute path inside workspace.") String filePath,
            @ToolParam(name = "old_string", description = "Exact text to replace.") String oldString,
            @ToolParam(name = "new_string", description = "Replacement text.") String newString,
            @ToolParam(name = "replace_all", required = false, description = "Replace every match. Default false.") Boolean replaceAll) {
        if (!StringUtils.hasText(filePath)) {
            return "file_path cannot be empty";
        }
        if (oldString == null || oldString.isEmpty()) {
            return "old_string cannot be empty";
        }
        if (newString == null) {
            newString = "";
        }
        if (oldString.equals(newString)) {
            return "new_string must be different from old_string";
        }
        if (isSensitivePath(filePath)) {
            return "Refused to directly edit sensitive file: " + filePath + ". Use propose_file_change if review is required.";
        }

        ResolvedWorkspacePath resolved = sandboxPathResolver.resolveExistingPath(rootPath(), filePath);
        if (!Files.isRegularFile(resolved.absolutePath())) {
            return "Path is not a regular file: " + resolved.relativePath();
        }
        try {
            byte[] oldBytes = Files.readAllBytes(resolved.absolutePath());
            if (looksBinary(oldBytes)) {
                return "Refused to edit binary file: " + resolved.relativePath();
            }
            String original = new String(oldBytes, StandardCharsets.UTF_8);
            int matches = countOccurrences(original, oldString);
            if (matches == 0) {
                return "old_string was not found in file: " + resolved.relativePath();
            }
            if (!Boolean.TRUE.equals(replaceAll) && matches > 1) {
                return "old_string matched multiple locations. Set replace_all=true or provide a more specific old_string.";
            }

            String updated = Boolean.TRUE.equals(replaceAll)
                    ? original.replace(oldString, newString)
                    : replaceFirstLiteral(original, oldString, newString);
            Files.write(resolved.absolutePath(), updated.getBytes(StandardCharsets.UTF_8));

            LineChangeStats stats = calculateLineChangeStats(splitLines(original), splitLines(updated));
            return "File written directly to current workspace.\n"
                    + "已编辑文件：1\n"
                    + "总变更：+" + stats.addedLines() + " / -" + stats.deletedLines() + "\n"
                    + "- " + resolved.relativePath() + " (MODIFY): +"
                    + stats.addedLines() + " / -" + stats.deletedLines();
        } catch (IOException e) {
            return "Edit failed: " + e.getMessage();
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

    private String formatWorkspacePatchResult(WorkspacePatchApplyResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("Patch 已直接应用到当前工作区。\n")
                .append("已编辑文件：").append(result.fileCount()).append("\n")
                .append("总变更：+").append(result.addedLines())
                .append(" / -").append(result.deletedLines()).append("\n");
        for (WorkspacePatchApplyResult.AppliedFile file : result.files()) {
            builder.append("- ")
                    .append(file.path())
                    .append(" (")
                    .append(file.changeType())
                    .append("): +")
                    .append(file.addedLines())
                    .append(" / -")
                    .append(file.deletedLines())
                    .append("\n");
        }
        return builder.toString();
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

    private record LineChangeStats(int addedLines, int deletedLines) {
    }

    private String readFileLines(String filePath, Integer offset, Integer limit) {
        ResolvedWorkspacePath resolved = sandboxPathResolver.resolveExistingPath(rootPath(), filePath);
        if (!Files.isRegularFile(resolved.absolutePath())) {
            return "Path is not a regular file: " + resolved.relativePath();
        }
        if (isSensitivePath(resolved.relativePath())) {
            return "Refused to read sensitive file: " + resolved.relativePath();
        }
        try {
            byte[] bytes = Files.readAllBytes(resolved.absolutePath());
            if (looksBinary(bytes)) {
                return "Refused to read binary file: " + resolved.relativePath();
            }
            List<String> lines = splitLines(new String(bytes, StandardCharsets.UTF_8));
            int startLine = offset != null && offset > 0 ? offset : 1;
            int maxLines = limit != null && limit > 0 ? Math.min(limit, 2000) : 2000;
            int startIndex = Math.max(0, startLine - 1);
            int endIndex = Math.min(lines.size(), startIndex + maxLines);

            StringBuilder result = new StringBuilder();
            result.append("File: ").append(resolved.relativePath()).append("\n")
                    .append("Lines: ").append(lines.size()).append("\n")
                    .append("Window: ").append(startLine).append("-").append(endIndex).append("\n\n");
            for (int i = startIndex; i < endIndex; i++) {
                result.append(String.format("%6d\t%s%n", i + 1, lines.get(i)));
            }
            if (endIndex < lines.size()) {
                result.append("\n[truncated: use offset=").append(endIndex + 1).append(" to continue]");
            }
            return result.toString();
        } catch (IOException e) {
            return "Read failed: " + e.getMessage();
        }
    }

    private String normalizeGlob(String pattern) {
        String normalized = pattern.replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private String replaceFirstLiteral(String text, String oldString, String newString) {
        int index = text.indexOf(oldString);
        if (index < 0) {
            return text;
        }
        return text.substring(0, index) + newString + text.substring(index + oldString.length());
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

