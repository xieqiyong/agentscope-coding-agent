package com.agentplatform.web.controller;

import com.agentplatform.runtime.model.AgentRunCommand;
import com.agentplatform.runtime.model.RuntimeEvent;
import com.agentplatform.runtime.model.RuntimeEventType;
import com.agentplatform.runtime.service.AgentRuntimeService;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 智能体运行时 Web 入口。
 * 项目约束要求新增接口优先使用 POST，因此流式接口也使用 POST + SseEmitter。
 */
@RestController
@RequestMapping("/api/agent-runtime")
public class AgentRuntimeController {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeController.class);

    @Resource
    private AgentRuntimeService agentRuntimeService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @PostMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AgentRunCommand command) {
        SseEmitter emitter = new SseEmitter(0L);
        executorService.submit(() -> {
            try {
                agentRuntimeService.executeStreaming(command, event -> sendEvent(emitter, event));
                emitter.complete();
            } catch (Exception e) {
                sendEvent(emitter, RuntimeEvent.of(null, null, RuntimeEventType.RUN_ERROR,
                        "运行异常", e.getMessage(), Map.of(), 0));
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, RuntimeEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getType() != null ? event.getType().name() : "RAW_EVENT")
                    .data(event, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            // 中文注释：客户端断开时不继续抛出异常，后台执行链路会自行收尾。
            log.warn("SSE 客户端已断开，type={}，runId={}，原因={}", event.getType(), event.getRunId(), e.getMessage());
        } catch (IllegalStateException e) {
            // 中文注释：连接已经完成或超时时，SseEmitter 可能抛出状态异常，这里只记录不拖垮主链路。
            log.warn("SSE 连接状态异常，type={}，runId={}，原因={}", event.getType(), event.getRunId(), e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
