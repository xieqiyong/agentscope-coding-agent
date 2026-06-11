package com.agentplatform.sandbox;

import com.agentplatform.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class SandboxPathResolver {

    public Path resolveWorkspaceRoot(String rootPath) {
        if (!StringUtils.hasText(rootPath)) {
            throw new BusinessException(400, "Workspace root path is required");
        }

        Path rawRoot = Paths.get(rootPath);
        if (!rawRoot.isAbsolute()) {
            throw new BusinessException(400, "Workspace root path must be absolute");
        }

        Path normalizedRoot = rawRoot.toAbsolutePath().normalize();
        if (!Files.exists(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(400, "Workspace root does not exist");
        }
        if (!Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(400, "Workspace root must be a directory");
        }
        if (Files.isSymbolicLink(normalizedRoot)) {
            throw new BusinessException(400, "Workspace root cannot be a symbolic link");
        }

        try {
            return normalizedRoot.toRealPath(LinkOption.NOFOLLOW_LINKS).normalize();
        } catch (IOException e) {
            throw new BusinessException(400, "Cannot resolve workspace root");
        }
    }

    public ResolvedWorkspacePath resolveExistingPath(String workspaceRootPath, String requestedPath) {
        return resolvePath(workspaceRootPath, requestedPath, true);
    }

    public ResolvedWorkspacePath resolvePath(String workspaceRootPath, String requestedPath, boolean mustExist) {
        Path root = resolveWorkspaceRoot(workspaceRootPath);
        Path candidate = StringUtils.hasText(requestedPath) ? Paths.get(requestedPath) : root;
        if (!candidate.isAbsolute()) {
            candidate = root.resolve(candidate);
        }
        candidate = candidate.toAbsolutePath().normalize();

        ensureInsideWorkspace(root, candidate);

        if (mustExist && !Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(404, "Path does not exist in workspace");
        }

        rejectSymlinkSegments(root, candidate);

        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            try {
                candidate = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS).normalize();
            } catch (IOException e) {
                throw new BusinessException(400, "Cannot resolve path in workspace");
            }
            ensureInsideWorkspace(root, candidate);
        }

        String relativePath = root.equals(candidate)
                ? ""
                : root.relativize(candidate).toString().replace(File.separatorChar, '/');
        return new ResolvedWorkspacePath(root, candidate, relativePath);
    }

    private void ensureInsideWorkspace(Path root, Path candidate) {
        if (!candidate.startsWith(root)) {
            throw new BusinessException(403, "Path escapes workspace root");
        }
    }

    private void rejectSymlinkSegments(Path root, Path candidate) {
        Path relative = root.relativize(candidate);
        Path cursor = root;
        for (Path segment : relative) {
            cursor = cursor.resolve(segment);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
                throw new BusinessException(403, "Symbolic links are not allowed in workspace paths");
            }
        }
    }
}
