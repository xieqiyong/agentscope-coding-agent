package com.agentplatform.runtime.multiagent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一次计划对应的任务依赖图。
 *
 * 该对象只表达节点、依赖和状态判断，不依赖 Spring、数据库或 AgentScope。
 */
public class TaskGraph {

    private final LinkedHashMap<String, AgentPlanStep> nodes;

    private TaskGraph(LinkedHashMap<String, AgentPlanStep> nodes) {
        this.nodes = nodes;
    }

    public static TaskGraph from(AgentPlan plan) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            throw new IllegalArgumentException("任务图不能为空");
        }

        normalizeLegacyDependencies(plan);
        LinkedHashMap<String, AgentPlanStep> nodes = new LinkedHashMap<>();
        for (AgentPlanStep step : plan.getSteps()) {
            if (step == null || step.getId() == null || step.getId().isBlank()) {
                throw new IllegalArgumentException("任务图节点 ID 不能为空");
            }
            String nodeId = step.getId().trim();
            step.setId(nodeId);
            if (nodes.putIfAbsent(nodeId, step) != null) {
                throw new IllegalArgumentException("任务图节点 ID 重复：" + nodeId);
            }
            if (step.getDependsOn() == null) {
                step.setDependsOn(new ArrayList<>());
            }
        }

        TaskGraph graph = new TaskGraph(nodes);
        graph.validateDependencies();
        graph.validateAcyclic();
        return graph;
    }

    /**
     * 旧计划没有图版本和依赖关系，继续按原有顺序执行，避免历史计划突然全部并行。
     */
    private static void normalizeLegacyDependencies(AgentPlan plan) {
        List<AgentPlanStep> steps = plan.getSteps();
        if (plan.getGraphVersion() >= 2) {
            return;
        }
        for (int i = 0; i < steps.size(); i++) {
            AgentPlanStep step = steps.get(i);
            if (step == null) {
                continue;
            }
            if (i == 0) {
                step.setDependsOn(new ArrayList<>());
            } else {
                AgentPlanStep previous = steps.get(i - 1);
                step.setDependsOn(previous != null && previous.getId() != null
                        ? new ArrayList<>(List.of(previous.getId()))
                        : new ArrayList<>());
            }
        }
    }

    public List<AgentPlanStep> readyNodes() {
        List<AgentPlanStep> ready = new ArrayList<>();
        for (AgentPlanStep node : nodes.values()) {
            TaskNodeStatus status = TaskNodeStatus.from(node.getStatus());
            if (status != TaskNodeStatus.PENDING && status != TaskNodeStatus.READY) {
                continue;
            }
            if (dependenciesCompleted(node)) {
                ready.add(node);
            }
        }
        return ready;
    }

    public boolean allCompleted() {
        return !nodes.isEmpty() && nodes.values().stream()
                .allMatch(node -> TaskNodeStatus.from(node.getStatus()) == TaskNodeStatus.COMPLETED);
    }

    public boolean hasFailed() {
        return nodes.values().stream()
                .anyMatch(node -> TaskNodeStatus.from(node.getStatus()) == TaskNodeStatus.FAILED);
    }

    public boolean hasWaitingNode() {
        return nodes.values().stream()
                .anyMatch(node -> TaskNodeStatus.from(node.getStatus()) == TaskNodeStatus.WAITING);
    }

    public List<String> unfinishedNodeIds() {
        return nodes.values().stream()
                .filter(node -> TaskNodeStatus.from(node.getStatus()) != TaskNodeStatus.COMPLETED)
                .map(AgentPlanStep::getId)
                .toList();
    }

    public int completedCount() {
        return (int) nodes.values().stream()
                .filter(node -> TaskNodeStatus.from(node.getStatus()) == TaskNodeStatus.COMPLETED)
                .count();
    }

    public List<AgentPlanStep> nodes() {
        return new ArrayList<>(nodes.values());
    }

    private boolean dependenciesCompleted(AgentPlanStep node) {
        for (String dependencyId : node.getDependsOn()) {
            AgentPlanStep dependency = nodes.get(dependencyId);
            if (dependency == null
                    || TaskNodeStatus.from(dependency.getStatus()) != TaskNodeStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    private void validateDependencies() {
        for (AgentPlanStep node : nodes.values()) {
            Set<String> uniqueDependencies = new HashSet<>();
            List<String> normalized = new ArrayList<>();
            for (String dependencyId : node.getDependsOn()) {
                if (dependencyId == null || dependencyId.isBlank()) {
                    continue;
                }
                String dependency = dependencyId.trim();
                if (dependency.equals(node.getId())) {
                    throw new IllegalArgumentException("任务图节点不能依赖自身：" + node.getId());
                }
                if (!nodes.containsKey(dependency)) {
                    throw new IllegalArgumentException("任务图依赖节点不存在：" + node.getId() + " -> " + dependency);
                }
                if (uniqueDependencies.add(dependency)) {
                    normalized.add(dependency);
                }
            }
            node.setDependsOn(normalized);
        }
    }

    private void validateAcyclic() {
        Map<String, Integer> indegrees = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (String nodeId : nodes.keySet()) {
            indegrees.put(nodeId, 0);
            outgoing.put(nodeId, new ArrayList<>());
        }
        for (AgentPlanStep node : nodes.values()) {
            indegrees.put(node.getId(), node.getDependsOn().size());
            for (String dependency : node.getDependsOn()) {
                outgoing.get(dependency).add(node.getId());
            }
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        indegrees.forEach((nodeId, indegree) -> {
            if (indegree == 0) {
                queue.add(nodeId);
            }
        });
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            visited++;
            for (String next : outgoing.get(current)) {
                int remaining = indegrees.computeIfPresent(next, (ignored, value) -> value - 1);
                if (remaining == 0) {
                    queue.addLast(next);
                }
            }
        }
        if (visited != nodes.size()) {
            throw new IllegalArgumentException("任务图存在循环依赖");
        }
    }
}
