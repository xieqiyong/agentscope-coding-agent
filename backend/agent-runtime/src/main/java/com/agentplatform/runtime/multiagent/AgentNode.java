package com.agentplatform.runtime.multiagent;

/**
 * 多 Agent 节点统一接口。
 * 中文注释：后续本地 subagent、远程 A2A agent 都可以适配成这个接口。
 */
public interface AgentNode {

    String nodeName();

    AgentNodeResult invoke(MultiAgentState state);
}
