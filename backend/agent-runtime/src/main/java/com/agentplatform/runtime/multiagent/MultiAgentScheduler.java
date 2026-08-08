package com.agentplatform.runtime.multiagent;

import com.agentplatform.runtime.model.AgentRunResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;

/**
 * 基于依赖就绪队列的多 Agent 调度器。
 *
 * 调度器只负责决定哪些节点可以运行以及等待并行结果，不感知模型、数据库和 SSE。
 */
public class MultiAgentScheduler {

    private final Executor executor;
    private final int maxConcurrency;

    public MultiAgentScheduler(Executor executor, int maxConcurrency) {
        if (executor == null) {
            throw new IllegalArgumentException("多 Agent 执行器不能为空");
        }
        this.executor = executor;
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }

    public ScheduleResult run(TaskGraph graph,
                              TaskExecutor taskExecutor,
                              ScheduleListener listener,
                              CancellationCheck cancellationCheck) {
        Map<String, AgentRunResult> results = new LinkedHashMap<>();
        listener.onGraphStarted(graph);

        while (!graph.allCompleted()) {
            cancellationCheck.check();
            if (graph.hasFailed() || graph.hasWaitingNode()) {
                break;
            }

            List<AgentPlanStep> readyNodes = graph.readyNodes();
            if (readyNodes.isEmpty()) {
                throw new IllegalStateException("任务图没有可执行节点，未完成节点：" + graph.unfinishedNodeIds());
            }

            List<AgentPlanStep> batch = selectBatch(readyNodes);
            CompletionService<NodeOutcome> completionService = new ExecutorCompletionService<>(executor);
            List<Future<NodeOutcome>> futures = new ArrayList<>();

            for (AgentPlanStep node : batch) {
                node.setStatus(TaskNodeStatus.RUNNING.value());
                listener.onNodeStarted(node);
                futures.add(completionService.submit(() -> executeNode(node, taskExecutor, cancellationCheck)));
            }

            try {
                for (int i = 0; i < batch.size(); i++) {
                    cancellationCheck.check();
                    NodeOutcome outcome = completionService.take().get();
                    AgentPlanStep node = outcome.node();
                    if (outcome.error() != null) {
                        node.setStatus(TaskNodeStatus.FAILED.value());
                        listener.onNodeFailed(node, outcome.error());
                        continue;
                    }

                    AgentRunResult result = outcome.result();
                    results.put(node.getId(), result);
                    if (result != null && "WAITING_APPROVAL".equalsIgnoreCase(result.getStatus())) {
                        node.setStatus(TaskNodeStatus.WAITING.value());
                        listener.onNodeWaiting(node, result);
                    } else if (result != null && "COMPLETED".equalsIgnoreCase(result.getStatus())) {
                        node.setStatus(TaskNodeStatus.COMPLETED.value());
                        listener.onNodeCompleted(node, result);
                    } else {
                        node.setStatus(TaskNodeStatus.FAILED.value());
                        String status = result != null ? result.getStatus() : "UNKNOWN";
                        listener.onNodeFailed(node, new IllegalStateException("节点返回非完成状态：" + status));
                    }
                }
            } catch (InterruptedException e) {
                futures.forEach(future -> future.cancel(true));
                Thread.currentThread().interrupt();
                cancellationCheck.check();
                throw new IllegalStateException("多 Agent 调度线程被中断", e);
            } catch (ExecutionException e) {
                futures.forEach(future -> future.cancel(true));
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new IllegalStateException("多 Agent 节点执行异常：" + cause.getMessage(), cause);
            }
        }

        ScheduleResult result = new ScheduleResult(
                graph.allCompleted(),
                graph.hasWaitingNode(),
                graph.hasFailed(),
                results,
                graph.unfinishedNodeIds()
        );
        listener.onGraphFinished(graph, result);
        return result;
    }

    /**
     * 同一个 Agent 的 Session 不并发写入；不同 Agent 的就绪节点才会进入同一批次。
     */
    private List<AgentPlanStep> selectBatch(List<AgentPlanStep> readyNodes) {
        List<AgentPlanStep> batch = new ArrayList<>();
        Set<String> occupiedAgents = new HashSet<>();
        for (AgentPlanStep node : readyNodes) {
            String agentKey = node.getAgentId() != null ? "agent:" + node.getAgentId() : "agent:default";
            if (!occupiedAgents.add(agentKey)) {
                continue;
            }
            batch.add(node);
            if (batch.size() >= maxConcurrency) {
                break;
            }
        }
        if (batch.isEmpty() && !readyNodes.isEmpty()) {
            batch.add(readyNodes.get(0));
        }
        return batch;
    }

    private NodeOutcome executeNode(AgentPlanStep node,
                                    TaskExecutor taskExecutor,
                                    CancellationCheck cancellationCheck) {
        try {
            cancellationCheck.check();
            AgentRunResult result = taskExecutor.execute(node);
            cancellationCheck.check();
            return new NodeOutcome(node, result, null);
        } catch (Throwable throwable) {
            return new NodeOutcome(node, null, throwable);
        }
    }

    @FunctionalInterface
    public interface TaskExecutor {
        AgentRunResult execute(AgentPlanStep node);
    }

    @FunctionalInterface
    public interface CancellationCheck {
        void check();
    }

    public interface ScheduleListener {
        default void onGraphStarted(TaskGraph graph) {
        }

        default void onNodeStarted(AgentPlanStep node) {
        }

        default void onNodeCompleted(AgentPlanStep node, AgentRunResult result) {
        }

        default void onNodeWaiting(AgentPlanStep node, AgentRunResult result) {
        }

        default void onNodeFailed(AgentPlanStep node, Throwable error) {
        }

        default void onGraphFinished(TaskGraph graph, ScheduleResult result) {
        }
    }

    private record NodeOutcome(AgentPlanStep node, AgentRunResult result, Throwable error) {
    }

    public record ScheduleResult(boolean completed,
                                 boolean waiting,
                                 boolean failed,
                                 Map<String, AgentRunResult> results,
                                 List<String> unfinishedNodeIds) {
    }
}
