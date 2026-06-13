package com.agentplatform.memory.service;

import com.agentplatform.memory.model.MemoryCaptureResult;
import com.agentplatform.persistence.entity.MemoryConflictEntity;
import com.agentplatform.persistence.entity.MemoryEntryEntity;
import com.agentplatform.persistence.enums.MemoryStatus;
import com.agentplatform.persistence.repository.MemoryConflictRepository;
import com.agentplatform.persistence.repository.MemoryEntryRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 长期记忆捕获服务。
 * 第一版只做规则捕获，不调用大模型，避免把记忆系统和模型稳定性绑死。
 */
@Service
public class MemoryCaptureService {

    private static final Pattern SECRET_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|token|password|secret|authorization|bearer|密钥|密码)\\s*[:=：]\\s*\\S{6,}");
    private static final Pattern SECRET_VALUE_PATTERN = Pattern.compile("(?i)\\b(sk-[a-z0-9_-]{10,}|ak-[a-z0-9_-]{10,})\\b");

    @Resource
    private MemoryEntryRepository memoryEntryRepository;

    @Resource
    private MemoryConflictRepository memoryConflictRepository;

    public MemoryCaptureResult captureAfterRun(Long workspaceId, Long agentId, String userId,
                                                Long conversationId, Long sourceMessageId,
                                                String userMessage, String assistantAnswer) {
        if (workspaceId == null || sourceMessageId == null || !StringUtils.hasText(userMessage)) {
            return MemoryCaptureResult.empty();
        }
        if (hasSensitiveSecret(userMessage)) {
            return new MemoryCaptureResult(0, 0, 0, 0, 1);
        }
        if (!StringUtils.hasText(userId)) {
            userId = "default";
        }

        List<MemoryCandidate> candidates = extractCandidates(userMessage, assistantAnswer);
        if (candidates.isEmpty()) {
            return MemoryCaptureResult.empty();
        }

        int captured = 0;
        int activated = 0;
        int pending = 0;
        int conflicts = 0;
        int skipped = 0;

        for (MemoryCandidate candidate : candidates) {
            if (!StringUtils.hasText(candidate.content())) {
                skipped++;
                continue;
            }
            if (existsSameSourceAndKey(sourceMessageId, candidate.normalizedKey())) {
                skipped++;
                continue;
            }

            List<MemoryEntryEntity> sameKeyMemories =
                    memoryEntryRepository.findByWorkspaceIdAndUserIdAndNormalizedKeyOrderByUpdatedAtDesc(
                            workspaceId, userId, candidate.normalizedKey());
            MemoryEntryEntity activeMemory = firstActiveMemory(sameKeyMemories);

            MemoryEntryEntity memory = new MemoryEntryEntity();
            memory.setWorkspaceId(workspaceId);
            memory.setAgentId(agentId);
            memory.setUserId(userId);
            memory.setMemoryType(candidate.memoryType());
            memory.setNormalizedKey(candidate.normalizedKey());
            memory.setContent(candidate.content());
            memory.setSourceConversationId(conversationId);
            memory.setSourceMessageId(sourceMessageId);
            memory.setConfidence(candidate.confidence());

            if (activeMemory != null && sameContent(activeMemory.getContent(), candidate.content())) {
                skipped++;
                continue;
            }

            if (activeMemory != null && !sameContent(activeMemory.getContent(), candidate.content())) {
                memory.setStatus(MemoryStatus.CONFLICT.name());
                memory.setReviewReason("同一 normalizedKey 下已经存在 ACTIVE 记忆，需要人工确认是否替换。");
                memory = memoryEntryRepository.save(memory);
                createConflict(workspaceId, activeMemory.getId(), memory.getId());
                conflicts++;
            } else if (candidate.autoActivate()) {
                memory.setStatus(MemoryStatus.ACTIVE.name());
                memory.setReviewReason("用户明确要求记住，且规则判断为低风险，自动生效。");
                memoryEntryRepository.save(memory);
                activated++;
            } else {
                memory.setStatus(MemoryStatus.PENDING.name());
                memory.setReviewReason("规则捕获到可能的长期记忆，等待审核。");
                memoryEntryRepository.save(memory);
                pending++;
            }
            captured++;
        }

        return new MemoryCaptureResult(captured, activated, pending, conflicts, skipped);
    }

    private List<MemoryCandidate> extractCandidates(String userMessage, String assistantAnswer) {
        String text = normalizeContent(userMessage);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        List<MemoryCandidate> candidates = new ArrayList<>();
        boolean explicitRemember = containsAny(text, "记住", "帮我记住", "请记住", "以后记得");
        boolean futureInstruction = containsAny(text, "以后", "后续", "每次", "下次", "默认");

        if (containsAny(text, "不要改", "不要修改", "别改", "禁止修改", "不能修改", "不要动")) {
            candidates.add(new MemoryCandidate(
                    "PROJECT_CONSTRAINT",
                    buildConstraintKey(text),
                    cleanupMemoryContent(text),
                    confidence(explicitRemember ? "0.9500" : "0.8500"),
                    explicitRemember
            ));
            return candidates;
        }

        if (containsAny(text, "我喜欢", "我希望", "回答风格", "你以后", "以后你", "直接给", "先讲", "先说", "中文注释")) {
            candidates.add(new MemoryCandidate(
                    explicitRemember || futureInstruction ? "WORKING_STYLE" : "USER_PREFERENCE",
                    buildWorkingStyleKey(text),
                    cleanupMemoryContent(text),
                    confidence(explicitRemember ? "0.9300" : "0.7800"),
                    explicitRemember
            ));
            return candidates;
        }

        if (containsAny(text, "这个项目", "本项目", "当前项目", "启动命令", "测试命令", "技术栈", "模块", "使用的是", "用的是")) {
            candidates.add(new MemoryCandidate(
                    "PROJECT_FACT",
                    buildProjectFactKey(text),
                    cleanupMemoryContent(text),
                    confidence(explicitRemember ? "0.9000" : "0.7200"),
                    explicitRemember
            ));
            return candidates;
        }

        if (explicitRemember || futureInstruction) {
            candidates.add(new MemoryCandidate(
                    "USER_PREFERENCE",
                    "USER_PREFERENCE:general",
                    cleanupMemoryContent(text),
                    confidence(explicitRemember ? "0.9000" : "0.7000"),
                    explicitRemember
            ));
        }

        return candidates;
    }

    private boolean existsSameSourceAndKey(Long sourceMessageId, String normalizedKey) {
        List<MemoryEntryEntity> sourceMemories = memoryEntryRepository.findBySourceMessageId(sourceMessageId);
        for (MemoryEntryEntity memory : sourceMemories) {
            if (normalizedKey.equals(memory.getNormalizedKey())) {
                return true;
            }
        }
        return false;
    }

    private MemoryEntryEntity firstActiveMemory(List<MemoryEntryEntity> memories) {
        for (MemoryEntryEntity memory : memories) {
            if (MemoryStatus.ACTIVE.name().equals(memory.getStatus())) {
                return memory;
            }
        }
        return null;
    }

    private void createConflict(Long workspaceId, Long existingMemoryId, Long candidateMemoryId) {
        MemoryConflictEntity conflict = new MemoryConflictEntity();
        conflict.setWorkspaceId(workspaceId);
        conflict.setExistingMemoryId(existingMemoryId);
        conflict.setCandidateMemoryId(candidateMemoryId);
        conflict.setConflictType("SAME_KEY_DIFFERENT_CONTENT");
        conflict.setStatus("PENDING");
        memoryConflictRepository.save(conflict);
    }

    private boolean hasSensitiveSecret(String text) {
        return SECRET_ASSIGNMENT_PATTERN.matcher(text).find()
                || SECRET_VALUE_PATTERN.matcher(text).find();
    }

    private boolean sameContent(String left, String right) {
        return normalizeForKey(left).equals(normalizeForKey(right));
    }

    private String buildConstraintKey(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("generated")) {
            return "PROJECT_CONSTRAINT:path:generated";
        }
        if (lower.contains("target")) {
            return "PROJECT_CONSTRAINT:path:target";
        }
        if (lower.contains("node_modules")) {
            return "PROJECT_CONSTRAINT:path:node_modules";
        }
        return "PROJECT_CONSTRAINT:write_policy";
    }

    private String buildWorkingStyleKey(String text) {
        if (containsAny(text, "中文注释", "注释")) {
            return "WORKING_STYLE:code_comment";
        }
        if (containsAny(text, "先讲", "先说", "设计", "方案")) {
            return "WORKING_STYLE:answer_order";
        }
        if (containsAny(text, "直接给", "直接改", "不要解释", "少解释")) {
            return "WORKING_STYLE:directness";
        }
        if (containsAny(text, "回答风格")) {
            return "WORKING_STYLE:response_style";
        }
        return "WORKING_STYLE:collaboration";
    }

    private String buildProjectFactKey(String text) {
        if (text.contains("启动命令")) {
            return "PROJECT_FACT:startup_command";
        }
        if (text.contains("测试命令")) {
            return "PROJECT_FACT:test_command";
        }
        if (text.contains("技术栈")) {
            return "PROJECT_FACT:tech_stack";
        }
        return "PROJECT_FACT:general";
    }

    private String cleanupMemoryContent(String text) {
        String cleaned = text.trim()
                .replaceFirst("^(请)?帮我记住[：:，,\\s]*", "")
                .replaceFirst("^请记住[：:，,\\s]*", "")
                .replaceFirst("^记住[：:，,\\s]*", "");
        return abbreviate(cleaned, 500);
    }

    private String normalizeContent(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    private String normalizeForKey(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s，。！？、,.!?:：;；\"'`]+", "");
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal confidence(String value) {
        return new BigDecimal(value);
    }

    private String abbreviate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private record MemoryCandidate(
            String memoryType,
            String normalizedKey,
            String content,
            BigDecimal confidence,
            boolean autoActivate
    ) {
    }
}
