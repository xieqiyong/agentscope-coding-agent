package com.agentplatform.runtime.multiagent;

/**
 * 图编排节点。
 * 中文注释：节点只负责读写共享状态，下一跳由 AgentGraph 的边选择器决定。
 */
@FunctionalInterface
public interface AgentGraphNode {

    void invoke(MultiAgentState state);
}
