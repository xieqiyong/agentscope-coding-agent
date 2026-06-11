package com.agentplatform.sandbox;

import java.nio.file.Path;

public record ResolvedWorkspacePath(
        Path rootPath,
        Path absolutePath,
        String relativePath
) {
}
