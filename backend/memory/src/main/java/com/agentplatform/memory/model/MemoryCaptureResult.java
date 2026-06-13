package com.agentplatform.memory.model;

/**
 * 记忆捕获结果。
 * 用于运行结束时把本轮捕获情况写入运行事件，方便调试和复盘。
 */
public record MemoryCaptureResult(
        int captured,
        int activated,
        int pending,
        int conflicts,
        int skipped
) {

    public static MemoryCaptureResult empty() {
        return new MemoryCaptureResult(0, 0, 0, 0, 0);
    }
}
