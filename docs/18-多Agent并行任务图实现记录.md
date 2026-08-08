# 多 Agent 并行任务图实现记录

## 本轮目标

把原来的线性计划：

```text
step 1 -> step 2 -> step 3
```

升级为能够真正并行执行的任务依赖图：

```text
架构师
  ├─> 前端专家 ─┐
  └─> 后端专家 ─┼─> 汇总审查
```

AgentScope 仍然负责单个 Agent 的 ReAct Loop、模型调用、工具调用和事件流。
平台负责依赖图、并行调度、节点状态、checkpoint、取消恢复和前端展示。

## 任务图协议

`AgentPlan` 新增：

```text
graphVersion
maxConcurrency
```

`AgentPlanStep` 新增：

```text
dependsOn
attempt
output
errorMessage
startedAt
finishedAt
```

示例：

```json
{
  "graphVersion": 2,
  "maxConcurrency": 3,
  "steps": [
    {
      "id": "architect",
      "agentRole": "ARCHITECT",
      "dependsOn": []
    },
    {
      "id": "frontend",
      "agentRole": "FRONTEND",
      "dependsOn": ["architect"]
    },
    {
      "id": "backend",
      "agentRole": "BACKEND",
      "dependsOn": ["architect"]
    },
    {
      "id": "review",
      "agentRole": "QA",
      "dependsOn": ["frontend", "backend"]
    }
  ]
}
```

旧计划没有 `graphVersion=2` 时，会自动补成线性依赖，避免历史计划突然全部并行。

## 调度规则

`TaskGraph` 负责：

- 节点 ID 唯一性校验。
- 依赖节点存在性校验。
- 自依赖和循环依赖检查。
- 根据已完成依赖计算 ready nodes。

`MultiAgentScheduler` 负责：

- 每轮获取依赖已满足的节点。
- 按 `maxConcurrency` 提交到共享有界线程池。
- 等待当前并行批次结果后继续计算下一批节点。
- 同一个 Agent 不进入同一并行批次，避免并发写同一个 AgentScope Session。
- 任一节点失败后停止调度后续依赖节点。
- 用户取消时中断当前 run 绑定的所有节点线程。

节点状态：

```text
pending -> in_progress -> completed
                       -> failed
                       -> waiting
                       -> cancelled
```

## 上下文与事件隔离

每个并行节点通过 `RuntimeContext.fork()` 获得独立上下文，然后再应用自己的 Agent 和模型配置。

节点事件统一增加：

```text
taskNodeId
stepId
agentId
agentName
agentRole
modelConfigId
modelName
dependsOn
```

前端收到带 `taskNodeId` 的 `ANSWER_DELTA` 后，不再写入公共回答文本，而是追加到对应计划节点的 `output`。
工具调用同样带节点和 Agent 信息，因此并行执行时可以判断具体是谁调用了什么工具。

## Checkpoint 与恢复

`agent_plan_state.plan_json` 现在保存完整任务图快照，包括：

- 节点依赖。
- 节点状态。
- 节点输出。
- 错误信息。
- 尝试次数和时间。

每个节点开始和结束时都会更新快照。

中断恢复时：

1. 保留已经 `completed` 的节点。
2. 把 `in_progress`、`cancelled`、`waiting` 等未完成节点恢复为 `pending`。
3. 重新计算所有依赖已满足的节点。
4. 从 ready queue 继续执行，而不是依赖单一 `nextStepIndex`。

查询可恢复计划时按 `updatedAt` 和 `id` 倒序选择最新记录，避免恢复到旧任务。

## 如何体验

1. 在同一个 workspace 创建并启用三个 Agent：技术架构师、前端专家、后端专家。
2. 给三个 Agent 分别配置清晰的描述、系统提示词和模型。
3. 在聊天页开启“多 Agent”。
4. 输入一个同时包含前后端工作的明确任务，或者先使用 `/plan` 查看任务图。
5. 确认计划中前端和后端节点拥有相同前置依赖，汇总节点同时依赖前端和后端。
6. 执行后观察计划卡片：前端和后端节点会同时显示运行状态，工具记录会显示所属 Agent。

建议体验任务：

```text
为当前项目增加一个简单的运行统计页面：架构师先确定接口契约，后端专家实现统计接口，前端专家实现页面，最后汇总检查前后端字段是否一致。
```

## 当前边界

- 本轮完成的是同一进程内的本地多 Agent 并行，不是 A2A。
- Nacos 不参与任务图调度，后续只用于远程 Agent 注册发现。
- 不同 Agent 可以并行，但同一个 Agent 的多个节点会串行执行。
- 多个 Agent 目前仍然访问同一个 workspace。Planner 会避免把可能修改同一文件的任务设为并行，但还没有 Git worktree 级硬隔离。
- Reviewer Agent 和失败后的 Repair Loop 尚未实现。
- 节点级 token 用量只统计当前 run；中断前节点的历史 token 尚未汇总到恢复后的 run。

下一步优先做 Git worktree 隔离和 Reviewer / Repair Loop，而不是先接 Nacos。
