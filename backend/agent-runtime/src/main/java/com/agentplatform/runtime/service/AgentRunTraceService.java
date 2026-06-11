package com.agentplatform.runtime.service;

import com.agentplatform.persistence.entity.AgentEventEntity;
import com.agentplatform.persistence.repository.AgentEventRepository;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventSink;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 运行时 trace 服务。
 * SSE 是用户体验主链路，事件落库是排障审计链路；落库不能阻塞首包返回。
 */
@Service
public class AgentRunTraceService {

    private static final Logger log = LoggerFactory.getLogger(AgentRunTraceService.class);

    @Resource
    private AgentEventRepository agentEventRepository;

    public void recordAndForward(RuntimeEvent event, RuntimeEventSink downstream) {
        if (event == null) {
            return;
        }

        forwardToClient(event, downstream);
        recordEvent(event);
    }

    private void forwardToClient(RuntimeEvent event, RuntimeEventSink downstream) {
        if (downstream == null) {
            return;
        }
        downstream.emit(event);
    }

    private void recordEvent(RuntimeEvent event) {
        try {
            AgentEventEntity entity = new AgentEventEntity();
            entity.setRunId(event.getRunId());
            entity.setEventType(event.getType() != null ? event.getType().name() : "RAW_EVENT");
            entity.setStage(event.getStage());
            entity.setContent(event.getContent());
            entity.setMetadataJson(toJson(event.getMetadata()));
            entity.setElapsedMs(event.getElapsedMs());
            agentEventRepository.save(entity);
        } catch (Exception e) {
            // 中文注释：运行事件落库失败不能拖垮智能体主链路，否则用户会感觉首包卡死。
            log.warn("运行事件保存失败，已跳过落库，type={}，runId={}，原因={}",
                    event.getType(), event.getRunId(), e.getMessage(), e);
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

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
