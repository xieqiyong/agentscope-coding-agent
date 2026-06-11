package com.agentplatform.runtime.model;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 平台统一运行时事件。
 * 前端 SSE、数据库 agent_events 和调试面板都使用这个事件结构。
 */
public class RuntimeEvent {

    private String eventId;
    private Long runId;
    private String traceId;
    private RuntimeEventType type;
    private String stage;
    private String content;
    private Map<String, Object> metadata;
    private long elapsedMs;
    private LocalDateTime createdAt;

    public RuntimeEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.metadata = new LinkedHashMap<>();
        this.createdAt = LocalDateTime.now();
    }

    public static RuntimeEvent of(Long runId, String traceId, RuntimeEventType type, String stage,
                                  String content, Map<String, Object> metadata, long elapsedMs) {
        RuntimeEvent event = new RuntimeEvent();
        event.setRunId(runId);
        event.setTraceId(traceId);
        event.setType(type);
        event.setStage(stage);
        event.setContent(content);
        event.setMetadata(metadata != null ? metadata : new LinkedHashMap<>());
        event.setElapsedMs(elapsedMs);
        return event;
    }

    public static RuntimeEvent error(Long runId, String traceId, String message, long elapsedMs) {
        return of(runId, traceId, RuntimeEventType.RUN_ERROR, "运行异常", message, Map.of(), elapsedMs);
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public RuntimeEventType getType() {
        return type;
    }

    public void setType(RuntimeEventType type) {
        this.type = type;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

