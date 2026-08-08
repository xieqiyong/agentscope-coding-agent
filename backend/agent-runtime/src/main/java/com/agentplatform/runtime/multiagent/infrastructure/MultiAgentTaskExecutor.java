package com.agentplatform.runtime.multiagent.infrastructure;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多 Agent 节点共享的有界线程池。
 */
@Component
public class MultiAgentTaskExecutor {

    @Resource
    private MultiAgentExecutionProperties properties;

    private ThreadPoolExecutor executor;

    @PostConstruct
    public void initialize() {
        int workerThreads = Math.max(1, properties.getWorkerThreads());
        int queueCapacity = Math.max(workerThreads, properties.getQueueCapacity());
        executor = new ThreadPoolExecutor(
                workerThreads,
                workerThreads,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new NamedThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
    }

    public Executor executor() {
        if (executor == null) {
            throw new IllegalStateException("多 Agent 工作线程池尚未初始化");
        }
        return executor;
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static class NamedThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "multi-agent-node-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
