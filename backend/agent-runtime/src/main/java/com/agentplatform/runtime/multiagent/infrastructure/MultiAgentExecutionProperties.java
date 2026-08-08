package com.agentplatform.runtime.multiagent.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 多 Agent 本地并行执行配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.runtime.multi-agent")
public class MultiAgentExecutionProperties {

    /**
     * 单张任务图最大并发节点数。
     */
    private int maxConcurrency = 4;

    /**
     * 所有任务图共享的工作线程数。
     */
    private int workerThreads = 16;

    /**
     * 等待执行的节点队列容量。
     */
    private int queueCapacity = 128;

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
}
