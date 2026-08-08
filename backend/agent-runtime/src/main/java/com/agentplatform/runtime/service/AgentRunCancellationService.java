package com.agentplatform.runtime.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent Run 取消信号服务。
 * 中文注释：这里保存的是运行期内存信号，数据库终态仍由 AgentRunLifecycleService 管理。
 */
@Service
public class AgentRunCancellationService {

    private final Map<Long, CancellationSignal> signals = new ConcurrentHashMap<>();

    /**
     * 绑定当前执行线程。并行任务图可能同时绑定多个节点线程。
     */
    public void bindCurrentThread(Long runId) {
        if (runId == null) {
            return;
        }
        signals.compute(runId, (id, existing) -> {
            CancellationSignal signal = existing != null ? existing : new CancellationSignal();
            signal.threads.add(Thread.currentThread());
            return signal;
        });
    }

    /**
     * 解除当前节点线程绑定，不清理整次运行的取消信号。
     */
    public void unbindCurrentThread(Long runId) {
        if (runId == null) {
            return;
        }
        CancellationSignal signal = signals.get(runId);
        if (signal != null) {
            signal.threads.remove(Thread.currentThread());
        }
    }

    public void cancel(Long runId, String reason) {
        if (runId == null) {
            return;
        }
        CancellationSignal signal = signals.computeIfAbsent(runId, id -> new CancellationSignal());
        signal.cancelled = true;
        signal.reason = StringUtils.hasText(reason) ? reason : "用户取消运行";
        for (Thread thread : signal.threads) {
            if (thread != null && thread != Thread.currentThread()) {
                thread.interrupt();
            }
        }
    }

    public boolean isCancelled(Long runId) {
        if (runId == null) {
            return false;
        }
        CancellationSignal signal = signals.get(runId);
        return signal != null && signal.cancelled;
    }

    public String reason(Long runId) {
        CancellationSignal signal = runId != null ? signals.get(runId) : null;
        if (signal == null || !StringUtils.hasText(signal.reason)) {
            return "用户取消运行";
        }
        return signal.reason;
    }

    public void assertNotCancelled(Long runId) {
        if (isCancelled(runId)) {
            throw new AgentRunCancelledException(reason(runId));
        }
    }

    public void unbind(Long runId) {
        if (runId == null) {
            return;
        }
        signals.remove(runId);
    }

    private static class CancellationSignal {
        private volatile boolean cancelled;
        private volatile String reason;
        private final Set<Thread> threads = ConcurrentHashMap.newKeySet();
    }
}
