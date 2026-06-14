package com.agentplatform.memory.service;

import com.agentplatform.common.exception.BusinessException;
import com.agentplatform.persistence.entity.MemoryConflictEntity;
import com.agentplatform.persistence.entity.MemoryEntryEntity;
import com.agentplatform.persistence.enums.MemoryStatus;
import com.agentplatform.persistence.repository.MemoryConflictRepository;
import com.agentplatform.persistence.repository.MemoryEntryRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 长期记忆应用服务。
 * 这里负责记忆管理页使用的查询、手动创建、审核和冲突处理。
 */
@Service
public class MemoryApplicationService {

    @Resource
    private MemoryEntryRepository memoryEntryRepository;

    @Resource
    private MemoryConflictRepository memoryConflictRepository;

    public List<Map<String, Object>> list(Long workspaceId, String userId, String status) {
        if (workspaceId == null) {
            throw new BusinessException(400, "workspaceId 不能为空");
        }
        String normalizedUserId = normalizeUserId(userId);
        List<MemoryEntryEntity> entries;
        if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status)) {
            entries = memoryEntryRepository.findByWorkspaceIdAndUserIdAndStatusOrderByUpdatedAtDesc(
                    workspaceId, normalizedUserId, normalizeStatus(status).name());
        } else {
            entries = memoryEntryRepository.findByWorkspaceIdAndUserIdOrderByUpdatedAtDesc(workspaceId, normalizedUserId);
        }
        return entries.stream().map(this::toDto).toList();
    }

    @Transactional
    public Map<String, Object> create(Long workspaceId, Long agentId, String userId, String type,
                                      String content, String normalizedKey) {
        if (workspaceId == null) {
            throw new BusinessException(400, "workspaceId 不能为空");
        }
        if (!StringUtils.hasText(type)) {
            throw new BusinessException(400, "记忆类型不能为空");
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(400, "记忆内容不能为空");
        }

        MemoryEntryEntity memory = new MemoryEntryEntity();
        memory.setWorkspaceId(workspaceId);
        memory.setAgentId(agentId);
        memory.setUserId(normalizeUserId(userId));
        memory.setMemoryType(normalizeMemoryType(type));
        memory.setNormalizedKey(StringUtils.hasText(normalizedKey)
                ? normalizedKey.trim()
                : buildManualKey(type, content));
        memory.setContent(content.trim());
        memory.setConfidence(new BigDecimal("1.0000"));
        memory.setStatus(MemoryStatus.ACTIVE.name());
        memory.setReviewReason("用户手动创建，默认直接生效。");
        return toDto(memoryEntryRepository.save(memory));
    }

    @Transactional
    public Map<String, Object> approve(Long id, String decidedBy) {
        MemoryEntryEntity memory = getMemory(id);
        if (MemoryStatus.ACTIVE.name().equals(memory.getStatus())) {
            return toDto(memory);
        }
        if (MemoryStatus.CONFLICT.name().equals(memory.getStatus())) {
            resolveConflictsByApprovingCandidate(memory, decidedBy);
        }
        memory.setStatus(MemoryStatus.ACTIVE.name());
        memory.setReviewReason("记忆已审核通过。");
        return toDto(memoryEntryRepository.save(memory));
    }

    @Transactional
    public Map<String, Object> reject(Long id, String decidedBy) {
        MemoryEntryEntity memory = getMemory(id);
        memory.setStatus(MemoryStatus.REJECTED.name());
        memory.setReviewReason("记忆已被拒绝。");
        MemoryEntryEntity saved = memoryEntryRepository.save(memory);
        closeCandidateConflicts(saved.getId(), "REJECTED", decidedBy);
        return toDto(saved);
    }

    @Transactional
    public Map<String, Object> disable(Long id, String decidedBy) {
        MemoryEntryEntity memory = getMemory(id);
        memory.setStatus(MemoryStatus.DISABLED.name());
        memory.setReviewReason("记忆已被禁用。");
        MemoryEntryEntity saved = memoryEntryRepository.save(memory);
        closeExistingConflicts(saved.getId(), "DISABLED", decidedBy);
        return toDto(saved);
    }

    private void resolveConflictsByApprovingCandidate(MemoryEntryEntity candidate, String decidedBy) {
        List<MemoryConflictEntity> conflicts = memoryConflictRepository.findByCandidateMemoryId(candidate.getId());
        for (MemoryConflictEntity conflict : conflicts) {
            if (!"PENDING".equals(conflict.getStatus())) {
                continue;
            }
            MemoryEntryEntity existing = memoryEntryRepository.findById(conflict.getExistingMemoryId()).orElse(null);
            if (existing != null && MemoryStatus.ACTIVE.name().equals(existing.getStatus())) {
                existing.setStatus(MemoryStatus.DISABLED.name());
                existing.setReviewReason("冲突处理中批准了新记忆，本条旧记忆自动禁用。");
                memoryEntryRepository.save(existing);
            }
            conflict.setStatus("RESOLVED");
            conflict.setResolution("APPROVED_CANDIDATE");
            conflict.setResolvedAt(LocalDateTime.now());
            memoryConflictRepository.save(conflict);
        }
    }

    private void closeCandidateConflicts(Long candidateMemoryId, String resolution, String decidedBy) {
        List<MemoryConflictEntity> conflicts = memoryConflictRepository.findByCandidateMemoryId(candidateMemoryId);
        for (MemoryConflictEntity conflict : conflicts) {
            if (!"PENDING".equals(conflict.getStatus())) {
                continue;
            }
            conflict.setStatus("RESOLVED");
            conflict.setResolution(resolution);
            conflict.setResolvedAt(LocalDateTime.now());
            memoryConflictRepository.save(conflict);
        }
    }

    private void closeExistingConflicts(Long existingMemoryId, String resolution, String decidedBy) {
        List<MemoryConflictEntity> conflicts = memoryConflictRepository.findByExistingMemoryId(existingMemoryId);
        for (MemoryConflictEntity conflict : conflicts) {
            if (!"PENDING".equals(conflict.getStatus())) {
                continue;
            }
            conflict.setStatus("RESOLVED");
            conflict.setResolution(resolution);
            conflict.setResolvedAt(LocalDateTime.now());
            memoryConflictRepository.save(conflict);
        }
    }

    private MemoryEntryEntity getMemory(Long id) {
        if (id == null) {
            throw new BusinessException(400, "记忆 ID 不能为空");
        }
        return memoryEntryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "记忆不存在：" + id));
    }

    private Map<String, Object> toDto(MemoryEntryEntity memory) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", memory.getId());
        dto.put("workspaceId", memory.getWorkspaceId());
        dto.put("agentId", memory.getAgentId());
        dto.put("userId", memory.getUserId());
        dto.put("type", memory.getMemoryType());
        dto.put("memoryType", memory.getMemoryType());
        dto.put("normalizedKey", memory.getNormalizedKey());
        dto.put("content", memory.getContent());
        dto.put("status", memory.getStatus());
        dto.put("confidence", memory.getConfidence() == null ? 0 : memory.getConfidence());
        dto.put("reviewReason", memory.getReviewReason());
        dto.put("sourceConversationId", memory.getSourceConversationId());
        dto.put("sourceMessageId", memory.getSourceMessageId());
        dto.put("createdAt", memory.getCreatedAt());
        dto.put("updatedAt", memory.getUpdatedAt());
        return dto;
    }

    private MemoryStatus normalizeStatus(String status) {
        try {
            return MemoryStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BusinessException(400, "不支持的记忆状态：" + status);
        }
    }

    private String normalizeMemoryType(String type) {
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeUserId(String userId) {
        return StringUtils.hasText(userId) ? userId : "default";
    }

    private String buildManualKey(String type, String content) {
        String normalized = content.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s，。！？、,.!?:：;；\"'`]+", "");
        int hash = normalized.hashCode();
        return normalizeMemoryType(type) + ":manual:" + Integer.toHexString(hash);
    }
}
