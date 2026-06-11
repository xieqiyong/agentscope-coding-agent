package com.agentplatform.runtime.service;

import com.agentplatform.persistence.entity.AgentEventEntity;
import com.agentplatform.persistence.repository.AgentEventRepository;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventSink;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 运行时 trace 服务。
 * 每个 RuntimeEvent 都先落库，再转发给外部 SSE sink，确保排障时可以回放完整链路。
 */
@Service
public class AgentRunTraceService {

    @Resource
    private AgentEventRepository agentEventRepository;

    public void recordAndForward(RuntimeEvent event, RuntimeEventSink downstream) {
        if (event == null) {
            return;
        }

        AgentEventEntity entity = new AgentEventEntity();
        entity.setRunId(event.getRunId());
        entity.setEventType(event.getType() != null ? event.getType().name() : "RAW_EVENT");
        entity.setStage(event.getStage());
        entity.setContent(event.getContent());
        entity.setMetadataJson(toJson(event.getMetadata()));
        entity.setElapsedMs(event.getElapsedMs());
        agentEventRepository.save(entity);

        if (downstream != null) {
            downstream.emit(event);
        }
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}

