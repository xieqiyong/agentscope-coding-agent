package com.agentplatform.memory.service;

import com.agentplatform.persistence.entity.MemoryEntryEntity;
import com.agentplatform.persistence.enums.MemoryStatus;
import com.agentplatform.persistence.repository.MemoryEntryRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 长期记忆上下文组装器。
 * 它负责决定哪些 ACTIVE 记忆可以注入 prompt，以及注入时的文本预算。
 */
@Service
public class MemoryContextAssembler {

    private static final int MAX_MEMORY_COUNT = 12;
    private static final int MAX_TOTAL_CHARS = 1800;
    private static final int MAX_SINGLE_MEMORY_CHARS = 240;

    @Resource
    private MemoryEntryRepository memoryEntryRepository;

    public List<MemoryEntryEntity> loadActiveMemories(Long workspaceId, String userId) {
        if (workspaceId == null) {
            return List.of();
        }
        // 单用户 MVP 阶段：按 workspace 维度加载 ACTIVE 记忆，不强制按 userId 过滤，
        // 保证手动创建和 Agent 自动捕获的 ACTIVE 记忆都能注入 prompt。
        List<MemoryEntryEntity> rows = memoryEntryRepository.findByWorkspaceIdAndStatusOrderByUpdatedAtDesc(
                workspaceId, MemoryStatus.ACTIVE.name());
        return selectInjectableMemories(rows);
    }

    public String assemblePromptSection(List<MemoryEntryEntity> memories) {
        List<MemoryEntryEntity> selected = selectInjectableMemories(memories);
        if (selected.isEmpty()) {
            return "- 暂无额外偏好或项目约束。\n";
        }

        StringBuilder builder = new StringBuilder();
        int usedChars = 0;
        for (MemoryEntryEntity memory : selected) {
            String line = "- [" + safe(memory.getMemoryType()) + "] "
                    + abbreviate(memory.getContent(), MAX_SINGLE_MEMORY_CHARS) + "\n";
            if (usedChars + line.length() > MAX_TOTAL_CHARS) {
                break;
            }
            builder.append(line);
            usedChars += line.length();
        }
        if (builder.length() == 0) {
            return "- 暂无额外偏好或项目约束。\n";
        }
        return builder.toString();
    }

    private List<MemoryEntryEntity> selectInjectableMemories(List<MemoryEntryEntity> memories) {
        if (memories == null || memories.isEmpty()) {
            return List.of();
        }
        List<MemoryEntryEntity> selected = new ArrayList<>();
        int usedChars = 0;
        for (MemoryEntryEntity memory : memories) {
            if (memory == null || !StringUtils.hasText(memory.getContent())) {
                continue;
            }
            if (!MemoryStatus.ACTIVE.name().equals(memory.getStatus())) {
                continue;
            }
            String content = abbreviate(memory.getContent(), MAX_SINGLE_MEMORY_CHARS);
            int nextChars = content.length() + safe(memory.getMemoryType()).length() + 8;
            if (selected.size() >= MAX_MEMORY_COUNT || usedChars + nextChars > MAX_TOTAL_CHARS) {
                break;
            }
            selected.add(memory);
            usedChars += nextChars;
        }
        return selected;
    }

    private String abbreviate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
