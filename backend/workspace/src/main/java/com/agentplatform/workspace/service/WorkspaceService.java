package com.agentplatform.workspace.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.WorkspaceEntity;
import com.agentplatform.persistence.repository.WorkspaceRepository;
import com.agentplatform.sandbox.SandboxPathResolver;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 工作区应用服务。
 */
@Service
public class WorkspaceService {

    @Resource
    private WorkspaceRepository workspaceRepository;

    @Resource
    private SandboxPathResolver sandboxPathResolver;

    /**
     * 列出所有工作区。
     */
    public List<WorkspaceEntity> listAll() {
        return workspaceRepository.findAll();
    }

    /**
     * 按 ID 查工作区。
     */
    public WorkspaceEntity getById(Long id) {
        return workspaceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "工作区不存在"));
    }

    /**
     * 注册新工作区。会校验路径合法性（跨平台）。
     */
    @Transactional
    public WorkspaceEntity register(String name, String rootPath, String description, String ownerId) {
        if (!StringUtils.hasText(name)) throw new BusinessException(400, "工作区名称不能为空");
        if (!StringUtils.hasText(rootPath)) throw new BusinessException(400, "根目录路径不能为空");

        // 校验路径：绝对路径、目录存在、不是符号链接（跨平台兼容）
        Path resolved = sandboxPathResolver.resolveWorkspaceRoot(rootPath);

        // 检查是否已注册
        String normalizedPath = resolved.toString();
        workspaceRepository.findByRootPath(normalizedPath).ifPresent(existing -> {
            throw new BusinessException(400, "该路径已注册为工作区: " + existing.getName());
        });

        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setName(name);
        entity.setRootPath(normalizedPath);
        entity.setDescription(description);
        entity.setOwnerId(StringUtils.hasText(ownerId) ? ownerId : "default");
        entity.setStatus("ACTIVE");
        return workspaceRepository.save(entity);
    }

    /**
     * 更新工作区信息。
     */
    @Transactional
    public WorkspaceEntity update(Long id, String name, String description) {
        WorkspaceEntity entity = getById(id);
        if (StringUtils.hasText(name)) entity.setName(name);
        if (description != null) entity.setDescription(description);
        return workspaceRepository.save(entity);
    }

    /**
     * 删除工作区。
     */
    @Transactional
    public void delete(Long id) {
        workspaceRepository.deleteById(id);
    }

    /**
     * 获取工作区文件树。
     * 跨平台兼容：使用 Java NIO Path 处理路径分隔符。
     */
    public List<Map<String, Object>> getFileTree(Long workspaceId, int maxDepth) {
        WorkspaceEntity workspace = getById(workspaceId);
        Path root = sandboxPathResolver.resolveWorkspaceRoot(workspace.getRootPath());

        if (maxDepth <= 0) maxDepth = 3;
        if (maxDepth > 10) maxDepth = 10;

        List<Map<String, Object>> result = new ArrayList<>();
        buildTree(root, root, result, 0, maxDepth);
        return result;
    }

    /**
     * 浏览本机磁盘和目录。
     * 只返回目录，不返回文件，给注册工作区弹窗选择根目录使用。
     */
    public Map<String, Object> browseLocalDirectories(String path) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> roots = listLocalRoots();
        result.put("roots", roots);

        if (!StringUtils.hasText(path)) {
            result.put("currentPath", null);
            result.put("parentPath", null);
            result.put("entries", roots);
            return result;
        }

        Path current = Path.of(path).toAbsolutePath().normalize();
        if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(400, "目录不存在或不可访问");
        }

        result.put("currentPath", current.toString());
        result.put("parentPath", current.getParent() != null ? current.getParent().toString() : null);
        result.put("entries", listChildDirectories(current));
        return result;
    }

    private void buildTree(Path root, Path current, List<Map<String, Object>> result,
                           int depth, int maxDepth) {
        if (depth >= maxDepth) return;

        java.io.File[] children = current.toFile().listFiles();
        if (children == null) return;

        for (java.io.File child : children) {
            // 跳过隐藏目录和常见大目录
            if (child.isHidden()) continue;
            String name = child.getName();
            if (isSkippedDirectory(name)) continue;

            String relativePath = root.relativize(child.toPath()).toString().replace(java.io.File.separatorChar, '/');

            Map<String, Object> node = new HashMap<>();
            node.put("key", relativePath);
            node.put("label", name);
            node.put("path", relativePath);
            node.put("isDirectory", child.isDirectory());

            if (child.isFile()) {
                node.put("size", child.length());
                node.put("modifiedAt", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(child.lastModified()));
            }

            if (child.isDirectory()) {
                List<Map<String, Object>> childrenList = new ArrayList<>();
                buildTree(root, child.toPath(), childrenList, depth + 1, maxDepth);
                if (!childrenList.isEmpty()) {
                    node.put("children", childrenList);
                }
            }

            result.add(node);
        }
    }

    private boolean isSkippedDirectory(String name) {
        return ".git".equals(name) || "node_modules".equals(name) || "target".equals(name)
                || ".idea".equals(name) || ".vscode".equals(name) || "__pycache__".equals(name)
                || ".gradle".equals(name) || "build".equals(name) || "dist".equals(name);
    }

    private List<Map<String, Object>> listLocalRoots() {
        List<Map<String, Object>> roots = new ArrayList<>();
        File[] files = File.listRoots();
        if (files == null) {
            return roots;
        }
        for (File file : files) {
            if (file == null || !file.exists()) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            String path = file.toPath().toAbsolutePath().normalize().toString();
            item.put("name", path);
            item.put("path", path);
            item.put("root", true);
            roots.add(item);
        }
        roots.sort(Comparator.comparing(item -> String.valueOf(item.get("path")).toLowerCase()));
        return roots;
    }

    private List<Map<String, Object>> listChildDirectories(Path current) {
        List<Map<String, Object>> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(current)) {
            stream
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isVisibleDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .forEach(path -> entries.add(directoryEntry(path)));
            return entries;
        } catch (IOException | SecurityException e) {
            throw new BusinessException(400, "目录不可访问: " + current);
        }
    }

    private boolean isVisibleDirectory(Path path) {
        try {
            return !Files.isHidden(path);
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }

    private Map<String, Object> directoryEntry(Path path) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", path.getFileName() != null ? path.getFileName().toString() : path.toString());
        item.put("path", path.toAbsolutePath().normalize().toString());
        item.put("root", false);
        return item;
    }
}
