package com.agentplatform.runtime.multiagent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 轻量级 Agent 图编排器。
 * 中文注释：这里不绑定 Spring Bean 生命周期，只表达一次运行内的节点和边。
 */
public class AgentGraph {

    public static final String END = "__END__";

    private final String startNode;
    private final Map<String, AgentGraphNode> nodes;
    private final Map<String, Function<MultiAgentState, String>> edges;

    private AgentGraph(String startNode,
                       Map<String, AgentGraphNode> nodes,
                       Map<String, Function<MultiAgentState, String>> edges) {
        this.startNode = startNode;
        this.nodes = nodes;
        this.edges = edges;
    }

    public static Builder builder(String startNode) {
        return new Builder(startNode);
    }

    public void run(MultiAgentState state) {
        String current = startNode;
        int hops = 0;
        while (current != null && !END.equals(current) && state.getTerminalResult() == null) {
            if (hops++ > 256) {
                throw new IllegalStateException("AgentGraph 执行超过最大跳转次数，可能存在循环未收敛");
            }
            AgentGraphNode node = nodes.get(current);
            if (node == null) {
                throw new IllegalStateException("AgentGraph 未注册节点：" + current);
            }
            state.setGraphNode(current);
            node.invoke(state);
            if (state.getTerminalResult() != null) {
                break;
            }
            Function<MultiAgentState, String> selector = edges.get(current);
            current = selector != null ? selector.apply(state) : END;
        }
    }

    public static class Builder {

        private final String startNode;
        private final Map<String, AgentGraphNode> nodes = new LinkedHashMap<>();
        private final Map<String, Function<MultiAgentState, String>> edges = new LinkedHashMap<>();

        private Builder(String startNode) {
            this.startNode = startNode;
        }

        public Builder addNode(String nodeId, AgentGraphNode node) {
            nodes.put(nodeId, node);
            return this;
        }

        public Builder addEdge(String from, String to) {
            edges.put(from, ignored -> to);
            return this;
        }

        public Builder addConditionalEdge(String from, Function<MultiAgentState, String> selector) {
            edges.put(from, selector);
            return this;
        }

        public AgentGraph build() {
            if (!nodes.containsKey(startNode)) {
                throw new IllegalStateException("AgentGraph 起始节点未注册：" + startNode);
            }
            return new AgentGraph(startNode, new LinkedHashMap<>(nodes), new LinkedHashMap<>(edges));
        }
    }
}
