# 平台级 ToolGuard 与 Interrupt 设计记录

这份文档记录平台级工具治理的第一轮实现。目标是让高风险工具确认不再完全依赖 AgentScope PermissionEngine，而是由平台自己掌握中断、审批和恢复。

## 背景

之前我们尝试用 AgentScope `PermissionEngine` 触发 `RequireUserConfirmEvent`。

这条链路设计上合理，但实际测试时出现了问题：

```text
Bash 工具已经执行
命令沙箱已经生效
但是确认卡片没有稳定弹出
```

原因可能是：

- AgentScope Session 从 Redis 恢复了旧 `AgentState`。
- 旧 `permissionContext` 没有新规则。
- AgentScope 内部某些判断仍然读取 `AgentState.permissionContext`。
- 工具名可能被不同适配层规范化。

所以我们把“高风险工具确认”上移到平台层。

## 和 LangGraph 的思想对应

LangGraph 常见运行思想：

```text
State
  ↓
Node
  ↓
Edge
  ↓
Checkpoint
  ↓
Interrupt
  ↓
Resume
```

我们当前对应关系：

```text
State       -> AgentRun / Conversation / RuntimeContext
Node        -> AgentScope ReActAgent / Tool 方法
Edge        -> AgentScope loop 内部下一步
Checkpoint  -> AgentScope SessionKey + 数据库消息和事件
Interrupt   -> RuntimeToolGuard 发出 CONFIRMATION_REQUIRED
Resume      -> approval-stream 继续执行挂起工具
```

核心思想：

```text
AgentScope 负责 agent loop
平台负责治理、审批、审计和恢复
```

## 当前执行链路

以 Bash 为例：

```text
用户发起 chat-stream
  ↓
AgentScope ReActAgent 运行
  ↓
模型调用 Bash
  ↓
Bash 工具方法先进入 RuntimeToolGuard
  ↓
RuntimeToolGuard 判断 Bash 是 CRITICAL
  ↓
RuntimeToolGuard 发出 CONFIRMATION_REQUIRED
  ↓
AgentRun 状态切到 WAITING_APPROVAL
  ↓
前端渲染工具确认卡片
  ↓
用户批准
  ↓
approval-stream 读取 approval_requests.detail_json
  ↓
平台直接执行挂起的 Bash 命令
  ↓
CommandSandboxService 二次校验
  ↓
返回工具结果事件和 assistant 消息
```

这里有两个关键点：

```text
1. 用户批准不等于命令一定能执行
2. 批准后仍然要过 CommandSandboxService
```

## 为什么 approval-stream 不再喂回模型

AgentScope 原生确认恢复是：

```text
ConfirmResult
  ↓
恢复 pending tool
  ↓
AgentScope 继续 loop
```

平台级 ToolGuard 的恢复是：

```text
approval_requests
  ↓
读取挂起工具参数
  ↓
平台直接执行工具
  ↓
返回工具结果
  ↓
结束本轮 run
```

这样做的原因是：

- 这条确认不是 AgentScope 原生 pending tool 产生的。
- 强行把确认结果喂回 AgentScope，容易变成“模型继续猜”。
- 平台自己执行挂起工具更可控。
- 审计、超时、沙箱、输出截断都在平台层。

## 当前第一轮范围

已接入平台 ToolGuard 的工具：

```text
Bash
Shell
run_command
```

仍然暂时走原有链路的工具：

```text
write_file
Write
Edit
apply_patch
```

后续治理增强可以逐步把写文件工具也迁移到平台 ToolGuard。

## 事件结构

平台 ToolGuard 会发出：

```text
RuntimeEventType.CONFIRMATION_REQUIRED
```

关键 metadata：

```json
{
  "sourceEvent": "RuntimeToolGuard",
  "requestType": "TOOL_PERMISSION",
  "approvalMode": "PLATFORM_TOOL_GUARD",
  "riskLevel": "CRITICAL",
  "toolCalls": [
    {
      "id": "platform-tool-xxx",
      "name": "Bash",
      "input": {
        "command": "git status"
      },
      "riskLevel": "CRITICAL"
    }
  ]
}
```

后端生命周期包装器会补充：

```json
{
  "approvalRequests": [
    {
      "approvalId": 1,
      "toolCallId": "platform-tool-xxx",
      "toolName": "Bash",
      "riskLevel": "CRITICAL",
      "status": "PENDING"
    }
  ],
  "approvalId": 1
}
```

前端仍然复用原来的确认卡片。

## 数据库存储

`approval_requests.detail_json` 会保存：

```json
{
  "replyId": "platform-tool-guard-platform-tool-xxx",
  "requestType": "TOOL_PERMISSION",
  "approvalMode": "PLATFORM_TOOL_GUARD",
  "riskLevel": "CRITICAL",
  "reason": "平台 ToolGuard 要求用户确认后再执行高风险工具",
  "toolCall": {
    "id": "platform-tool-xxx",
    "name": "Bash",
    "input": {
      "command": "git status"
    }
  }
}
```

这个结构就是平台自己的 pending action。

## 和 checkpoint 的关系

Checkpoint 解决：

```text
从哪里恢复上下文
```

ToolGuard Interrupt 解决：

```text
什么时候暂停
暂停后等谁决策
决策后执行什么动作
```

所以它们不是同一个东西。

完整理解：

```text
状态机决定 run 能不能从 RUNNING 到 WAITING_APPROVAL
审批表保存 pending action
checkpoint 保存上下文现场
approval-stream 负责 resume
```

## 审批恢复时的模型配置

这次测试暴露了一个恢复链路问题：

```text
首轮 chat-stream 带了 modelBaseUrl / modelName / apiKey
  ↓
工具触发审批，run 进入 WAITING_APPROVAL
  ↓
用户点击批准，approval-stream 继续执行
  ↓
approval-stream 没带模型配置
  ↓
AgentScopeRuntimeAdapter 恢复模型调用时报：模型地址 baseUrl 不能为空
```

这个问题说明：审批恢复不是一个孤立按钮，它必须能恢复一次 Agent run 所需的运行配置。

当前修正策略：

- 前端 approval-stream 补传当前默认模型配置。
- 后端构建 RuntimeContext 时，如果 Agent 没绑定模型配置，就兜底使用默认模型配置。
- Controller 只在 service 没有发出 `RUN_ERROR` 时补发兜底错误，避免同一次失败出现两个 `RUN_ERROR`。

更长期的主流做法是：run 创建时把本次使用的模型配置快照保存下来，恢复时优先读取 run 快照，而不是依赖前端再次传参。

## 当前限制

第一轮平台 ToolGuard 还有几个限制：

- 只接了命令类工具。
- 不做 stdout/stderr 实时流。
- 不做后台任务。
- 不做进程树 kill。
- 不做环境变量白名单。
- 平台审批后直接执行工具，不再让模型继续总结。

这些是后续治理增强继续做的内容。

## fallback 放在哪里

fallback 是下一块治理能力，不属于 ToolGuard 本身。

它应该放在三层：

```text
模型 fallback
  主模型失败 -> 备用模型
  流式断线 -> 非流式重试
  网关超时 -> 降级模型

工具 fallback
  rg 不可用 -> Java 文件遍历搜索
  npm test 失败 -> 返回失败原因，不自动换危险命令
  命令超时 -> kill 后返回 TIMEOUT

运行 fallback
  AgentScope loop 失败 -> 保留 run 状态和 trace
  approval 恢复失败 -> 保留 pending action，允许重试
  记忆捕获失败 -> 跳过，不影响主流程
```

我们后续学习治理增强时，可以按这个顺序做：

```text
1. 工具超时和审计增强
2. 模型调用 fallback
3. 工具 fallback
4. run 级失败恢复
```

## 面试讲法

可以这样讲：

```text
我没有把高风险工具确认完全绑定在框架内部。

AgentScope 负责 ReAct loop，但工具执行前会先进入平台 ToolGuard。
ToolGuard 根据 toolName、风险等级、workspace、命令策略判断 ALLOW / ASK / DENY。

ASK 时，平台发出 CONFIRMATION_REQUIRED，把 pending action 写入 approval_requests，
AgentRun 状态切到 WAITING_APPROVAL。

用户在前端批准后，approval-stream 读取 pending action，平台直接执行挂起工具，
执行前仍然经过命令沙箱。

这个设计和 LangGraph interrupt/resume 思想一致，但治理权在平台层，
更适合做审计、权限、沙箱、fallback 和多 Agent 共享治理。
```
