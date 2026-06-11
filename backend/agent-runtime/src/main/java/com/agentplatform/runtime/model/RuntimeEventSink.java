package com.agentplatform.runtime.model;

/**
 * 运行时事件出口。
 * service 层只关心 emit，不绑定 SSE 或具体前端协议。
 */
@FunctionalInterface
public interface RuntimeEventSink {

    void emit(RuntimeEvent event);

    static RuntimeEventSink noop() {
        return event -> {
        };
    }
}

