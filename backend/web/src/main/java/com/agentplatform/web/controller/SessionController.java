package com.agentplatform.web.controller;

import com.agentplatform.common.dto.ApiResponse;
import com.agentplatform.persistence.entity.AgentEventEntity;
import com.agentplatform.persistence.entity.AgentRunEntity;
import com.agentplatform.persistence.entity.ConversationEntity;
import com.agentplatform.persistence.entity.ConversationMessageEntity;
import com.agentplatform.persistence.repository.AgentEventRepository;
import com.agentplatform.persistence.repository.AgentRunRepository;
import com.agentplatform.persistence.repository.ConversationMessageRepository;
import com.agentplatform.persistence.repository.ConversationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理接口。
 * MVP 阶段直接操作 Repository，后续可迁移到 ConversationApplicationService。
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private ConversationMessageRepository conversationMessageRepository;

    @Resource
    private AgentRunRepository agentRunRepository;

    @Resource
    private AgentEventRepository agentEventRepository;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 查询工作区下的会话列表。
     */
    @GetMapping
    public ApiResponse<List<ConversationEntity>> list(@RequestParam Long workspaceId) {
        List<ConversationEntity> sessions = conversationRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        return ApiResponse.success(sessions);
    }

    /**
     * 获取单个会话详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<ConversationEntity> getById(@PathVariable Long id) {
        return conversationRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "会话不存在"));
    }

    /**
     * 创建新会话。
     */
    @PostMapping
    public ApiResponse<ConversationEntity> create(@RequestBody Map<String, Object> body) {
        ConversationEntity entity = new ConversationEntity();
        entity.setTitle((String) body.getOrDefault("title", "新会话"));
        entity.setStatus("ACTIVE");

        if (body.get("workspaceId") != null) {
            entity.setWorkspaceId(toLong(body.get("workspaceId")));
        }
        if (body.get("agentId") != null) {
            entity.setAgentId(toLong(body.get("agentId")));
        }

        ConversationEntity saved = conversationRepository.save(entity);
        return ApiResponse.success(saved);
    }

    /**
     * 删除会话。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        conversationRepository.deleteById(id);
        return ApiResponse.success(null);
    }

    /**
     * 查询会话消息列表。
     */
    @GetMapping("/{id}/messages")
    public ApiResponse<?> listMessages(@PathVariable Long id) {
        return ApiResponse.success(conversationMessageRepository.findByConversationIdOrderByCreatedAtAsc(id));
    }

    /**
     * 查询会话完整时间线。
     * 中文注释：普通消息存在 conversation_messages，工具轨迹存在 agent_events；
     * 前端刷新后需要这里把两类数据重新组装成同一条可渲染消息。
     */
    @PostMapping("/{id}/timeline")
    public ApiResponse<?> timeline(@PathVariable Long id) {
        List<ConversationMessageEntity> messages = conversationMessageRepository.findByConversationIdOrderByCreatedAtAsc(id);
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (ConversationMessageEntity message : messages) {
            timeline.add(toMessageDto(message));
        }

        List<AgentRunEntity> runs = new ArrayList<>(agentRunRepository.findByConversationIdOrderByStartedAtDesc(id));
        runs.sort(this::compareRunOrder);
        for (AgentRunEntity run : runs) {
            attachToolCalls(timeline, run, rebuildToolCalls(run.getId()));
            attachPlan(timeline, run, rebuildPlan(run.getId()));
            attachPlanStepStatuses(timeline, run, rebuildPlanStepStatuses(run.getId()));
        }
        return ApiResponse.success(timeline);
    }

    private Long toLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) return Long.parseLong((String) value);
        return null;
    }

    private int compareRunOrder(AgentRunEntity left, AgentRunEntity right) {
        int timeCompare = compareNullableTime(left.getStartedAt(), right.getStartedAt());
        if (timeCompare != 0) {
            return timeCompare;
        }
        return compareNullableLong(left.getId(), right.getId());
    }

    private int compareNullableTime(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return left.compareTo(right);
    }

    private int compareNullableLong(Long left, Long right) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return left.compareTo(right);
    }

    private Map<String, Object> toMessageDto(ConversationMessageEntity message) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", String.valueOf(message.getId()));
        dto.put("conversationId", message.getConversationId());
        dto.put("sessionId", String.valueOf(message.getConversationId()));
        dto.put("role", message.getRole() != null ? message.getRole().toLowerCase() : "");
        dto.put("content", message.getContent());
        dto.put("timestamp", message.getCreatedAt());
        dto.put("createdAt", message.getCreatedAt());
        dto.put("toolCalls", new ArrayList<>());
        dto.put("plan", null);
        return dto;
    }

    @SuppressWarnings("unchecked")
    private void attachToolCalls(List<Map<String, Object>> timeline, AgentRunEntity run, List<Map<String, Object>> toolCalls) {
        if (toolCalls.isEmpty()) {
            return;
        }

        int userIndex = findUserMessageIndex(timeline, run);

        for (int i = Math.max(0, userIndex + 1); i < timeline.size(); i++) {
            Map<String, Object> message = timeline.get(i);
            if (!"assistant".equals(message.get("role"))) {
                continue;
            }
            List<Map<String, Object>> existing = (List<Map<String, Object>>) message.get("toolCalls");
            if (existing == null) {
                existing = new ArrayList<>();
                message.put("toolCalls", existing);
            }
            existing.addAll(toolCalls);
            return;
        }
    }

    /**
     * 中文注释：计划卡片也从 agent_events 重建，避免前端刷新后只剩普通 Markdown。
     */
    private void attachPlan(List<Map<String, Object>> timeline, AgentRunEntity run, Map<String, Object> plan) {
        if (plan == null || plan.isEmpty()) {
            return;
        }

        int userIndex = findUserMessageIndex(timeline, run);

        for (int i = Math.max(0, userIndex + 1); i < timeline.size(); i++) {
            Map<String, Object> message = timeline.get(i);
            if (!"assistant".equals(message.get("role"))) {
                continue;
            }
            message.put("plan", plan);
            return;
        }

        Map<String, Object> synthetic = new LinkedHashMap<>();
        synthetic.put("id", "plan-" + run.getId());
        synthetic.put("conversationId", run.getConversationId());
        synthetic.put("sessionId", String.valueOf(run.getConversationId()));
        synthetic.put("role", "assistant");
        synthetic.put("content", "");
        synthetic.put("timestamp", run.getFinishedAt() != null ? run.getFinishedAt() : run.getStartedAt());
        synthetic.put("createdAt", run.getFinishedAt() != null ? run.getFinishedAt() : run.getStartedAt());
        synthetic.put("toolCalls", new ArrayList<>());
        synthetic.put("plan", plan);

        int insertAt = userIndex >= 0 ? Math.min(userIndex + 1, timeline.size()) : timeline.size();
        timeline.add(insertAt, synthetic);
    }

    /**
     * 中文注释：计划执行是另一条 run，步骤状态事件需要折回前面那张计划卡片，否则刷新后会回到 pending。
     */
    @SuppressWarnings("unchecked")
    private void attachPlanStepStatuses(List<Map<String, Object>> timeline,
                                        AgentRunEntity run,
                                        List<Map<String, Object>> statuses) {
        if (statuses.isEmpty()) {
            return;
        }

        int userIndex = findUserMessageIndex(timeline, run);
        Map<String, Object> planMessage = findNearestPlanMessageBefore(timeline, userIndex);
        if (planMessage == null) {
            planMessage = findNearestPlanMessageAfter(timeline, userIndex);
        }
        if (planMessage == null) {
            return;
        }

        Map<String, Object> plan = normalizeMap(planMessage.get("plan"));
        if (plan == null) {
            return;
        }
        Object rawSteps = plan.get("steps");
        if (!(rawSteps instanceof List<?> steps)) {
            return;
        }

        for (Map<String, Object> status : statuses) {
            String stepId = firstString(status.get("stepId"));
            String stepStatus = firstString(status.get("status"));
            if (!StringUtils.hasText(stepId) || !StringUtils.hasText(stepStatus)) {
                continue;
            }
            for (Object item : steps) {
                if (!(item instanceof Map<?, ?> rawStep)) {
                    continue;
                }
                Map<String, Object> step = (Map<String, Object>) rawStep;
                if (stepId.equals(String.valueOf(step.get("id")))) {
                    step.put("status", stepStatus);
                    copyIfPresent(status, step, "agentId");
                    copyIfPresent(status, step, "agentName");
                    copyIfPresent(status, step, "agentRole");
                    copyIfPresent(status, step, "modelConfigId");
                    copyIfPresent(status, step, "modelName");
                }
            }
        }
        planMessage.put("plan", plan);
    }

    private Map<String, Object> rebuildPlan(Long runId) {
        List<AgentEventEntity> events = agentEventRepository.findByRunIdOrderByIdAsc(runId);
        for (AgentEventEntity event : events) {
            if (!"PLAN_CREATED".equals(event.getEventType())) {
                continue;
            }
            Map<String, Object> metadata = parseMetadata(event.getMetadataJson());
            Map<String, Object> plan = normalizeMap(metadata.get("plan"));
            if (plan != null && !plan.isEmpty()) {
                return plan;
            }
        }
        return null;
    }

    private List<Map<String, Object>> rebuildPlanStepStatuses(Long runId) {
        List<AgentEventEntity> events = agentEventRepository.findByRunIdOrderByIdAsc(runId);
        List<Map<String, Object>> statuses = new ArrayList<>();
        for (AgentEventEntity event : events) {
            if (!"PLAN_STEP_STATUS_CHANGED".equals(event.getEventType())) {
                continue;
            }
            Map<String, Object> metadata = parseMetadata(event.getMetadataJson());
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("stepId", firstString(metadata.get("stepId")));
            status.put("status", firstString(metadata.get("status")));
            status.put("agentName", firstString(metadata.get("agentName")));
            status.put("agentRole", firstString(metadata.get("agentRole")));
            status.put("agentId", metadata.get("agentId"));
            status.put("modelConfigId", metadata.get("modelConfigId"));
            status.put("modelName", firstString(metadata.get("modelName")));
            status.put("stepTitle", firstString(metadata.get("stepTitle")));
            statuses.add(status);
        }
        return statuses;
    }

    private List<Map<String, Object>> rebuildToolCalls(Long runId) {
        List<AgentEventEntity> events = agentEventRepository.findByRunIdOrderByIdAsc(runId);
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Map<String, Object>> byCallId = new LinkedHashMap<>();

        for (AgentEventEntity event : events) {
            String type = event.getEventType();
            Map<String, Object> metadata = parseMetadata(event.getMetadataJson());
            String callId = firstString(metadata.get("callId"), metadata.get("toolCallId"), event.getId());
            String toolName = firstString(metadata.get("toolName"), metadata.get("tool"), event.getStage());

            if ("TOOL_CALL_STARTED".equals(type)) {
                Map<String, Object> toolCall = findOrCreateToolCall(toolCalls, byCallId, callId, toolName);
                toolCall.put("status", "running");
                toolCall.put("startedAt", toEpochMs(event.getCreatedAt()));
            } else if ("TOOL_CALL_ARGS_DELTA".equals(type)) {
                Map<String, Object> toolCall = findOrCreateToolCall(toolCalls, byCallId, callId, toolName);
                String argsText = String.valueOf(toolCall.getOrDefault("argsText", ""));
                argsText = argsText + safe(event.getContent());
                toolCall.put("argsText", argsText);
                toolCall.put("args", parseArgs(argsText));
            } else if ("TOOL_RESULT_STARTED".equals(type)) {
                Map<String, Object> toolCall = findOrCreateToolCall(toolCalls, byCallId, callId, toolName);
                toolCall.put("status", "running");
            } else if ("TOOL_RESULT_DELTA".equals(type) || "TOOL_RESULT_DATA_DELTA".equals(type)) {
                Map<String, Object> toolCall = findOrCreateToolCall(toolCalls, byCallId, callId, toolName);
                String result = String.valueOf(toolCall.getOrDefault("result", ""));
                toolCall.put("result", result + safe(event.getContent()));
            } else if ("TOOL_RESULT_FINISHED".equals(type)) {
                Map<String, Object> toolCall = findOrCreateToolCall(toolCalls, byCallId, callId, toolName);
                toolCall.put("status", "completed");
                toolCall.put("durationMs", event.getElapsedMs());
            }
        }

        for (Map<String, Object> toolCall : toolCalls) {
            if (!StringUtils.hasText(String.valueOf(toolCall.get("toolName")))) {
                toolCall.put("toolName", "unknown_tool");
            }
            if (!toolCall.containsKey("status")) {
                toolCall.put("status", "completed");
            }
        }
        return toolCalls;
    }

    private Map<String, Object> normalizeMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return normalized;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            target.put(key, value);
        }
    }

    private int findUserMessageIndex(List<Map<String, Object>> timeline, AgentRunEntity run) {
        for (int i = 0; i < timeline.size(); i++) {
            Object messageId = timeline.get(i).get("id");
            if (String.valueOf(run.getUserMessageId()).equals(String.valueOf(messageId))) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, Object> findNearestPlanMessageBefore(List<Map<String, Object>> timeline, int userIndex) {
        int start = userIndex >= 0 ? Math.min(userIndex, timeline.size() - 1) : timeline.size() - 1;
        for (int i = start; i >= 0; i--) {
            Map<String, Object> message = timeline.get(i);
            if ("assistant".equals(message.get("role")) && normalizeMap(message.get("plan")) != null) {
                return message;
            }
        }
        return null;
    }

    private Map<String, Object> findNearestPlanMessageAfter(List<Map<String, Object>> timeline, int userIndex) {
        int start = Math.max(0, userIndex + 1);
        for (int i = start; i < timeline.size(); i++) {
            Map<String, Object> message = timeline.get(i);
            if ("assistant".equals(message.get("role")) && normalizeMap(message.get("plan")) != null) {
                return message;
            }
        }
        return null;
    }

    private Map<String, Object> findOrCreateToolCall(List<Map<String, Object>> toolCalls,
                                                     Map<String, Map<String, Object>> byCallId,
                                                     String callId,
                                                     String toolName) {
        Map<String, Object> existing = byCallId.get(callId);
        if (existing != null) {
            if (StringUtils.hasText(toolName) && !StringUtils.hasText(String.valueOf(existing.get("toolName")))) {
                existing.put("toolName", toolName);
            }
            return existing;
        }

        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("callId", callId);
        toolCall.put("toolName", toolName);
        toolCall.put("args", new LinkedHashMap<>());
        toolCall.put("argsText", "");
        toolCall.put("result", "");
        toolCall.put("status", "running");
        toolCalls.add(toolCall);
        byCallId.put(callId, toolCall);
        return toolCall;
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> parseArgs(String argsText) {
        if (!StringUtils.hasText(argsText)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(argsText, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("_raw", argsText);
            return raw;
        }
    }

    private String firstString(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private long toEpochMs(LocalDateTime time) {
        return time == null ? 0L : java.sql.Timestamp.valueOf(time).getTime();
    }
}
