# Coding Agent 学习路线

这份文档用于记录我们学习和实现 Coding Agent Runtime 的完整顺序。每完成一块，就更新状态，方便知道当前走到哪里。

## 进度总览

| 阶段 | 主题 | 状态 | 目标 |
| --- | --- | --- | --- |
| 1 | Agent Loop 基础 | 已完成 | 理解 ReActAgent 如何循环调用模型、工具和事件流 |
| 2 | RAG 与记忆基础 | 已完成 | 跑通文档检索、短期记忆、摘要和长期记忆候选 |
| 3 | 工具调用可观测 | 已完成 | 把模型输出、工具调用、工具结果通过 SSE 展示出来 |
| 4 | 文件级沙箱 | 已完成第一轮 | 限制 workspace 内读写，拦截路径逃逸、敏感文件和 symlink |
| 5 | SessionKey / AgentState | 下一步 | 学习 AgentScope 内部 checkpoint 和 session 状态恢复 |
| 6 | 运行状态机 | 未开始 | 建模 RUNNING、WAITING_APPROVAL、RESUMED、FAILED 等状态 |
| 7 | 权限治理 | 未开始 | 给文件、命令、网络工具建立 allow / ask / deny 策略 |
| 8 | 命令级沙箱 | 未开始 | 实现 Bash、超时、stdout/stderr 流式、后台任务和危险命令拦截 |
| 9 | Checkpoint + Pending Action | 未开始 | 用户确认后从挂起点继续，异常后能恢复或补偿 |
| 10 | 回滚和补偿 | 未开始 | 文件快照、多文件事务、run 失败后的恢复策略 |
| 11 | 多 Agent / 计划型 Agent | 未开始 | 学习 Plan、Observe、Execute、Review 等 Agent 形态 |
| 12 | 产品化治理 | 未开始 | 审计、限流、fallback、workspace 管理和前端体验打磨 |

## 已完成内容

### 1. Agent Loop 基础

已经理解并跑通：

- ReActAgent 启动后如何进入循环。
- 模型如何根据上下文决定回答或调用工具。
- tools / skills 如何暴露给模型。
- AgentScope 事件如何被转换成平台 RuntimeEvent。
- Reactor `doOnNext` 如何观察流式事件。
- SSE 如何把运行过程推给前端。

核心认知：

```text
Agent Loop = 模型推理 -> 工具调用 -> 工具结果 -> 再次模型推理
```

### 2. RAG 与记忆基础

已经理解并跑通：

- 文档上传、解析、切分。
- embedding 生成。
- Milvus 向量检索。
- ES 关键词检索。
- 向量检索和混合检索的效果差异。
- 短期会话记忆。
- 滑动窗口 + 摘要。
- 长期记忆的候选、审核、置信度和冲突。

核心认知：

```text
短期记忆解决当前会话上下文
摘要解决单会话超长
长期记忆解决跨会话稳定事实
```

### 3. 工具调用可观测

已经实现：

- 工具调用开始事件。
- 工具参数增量事件。
- 工具结果增量事件。
- 工具调用完成事件。
- 前端工具轨迹折叠展示。
- `Edited xxx (+a -d)` 风格变更摘要。
- 刷新后通过 timeline 恢复工具轨迹。

核心认知：

```text
模型不负责渲染 UI
模型只触发工具
Runtime 产生结构化事件
前端根据事件稳定渲染
```

### 4. 文件级沙箱

已经实现第一轮：

- workspace root 限制。
- 路径逃逸拦截。
- symlink 拦截。
- 敏感文件拦截。
- workspace 内普通文件直接读写。
- patch 应用和最小回滚。
- 工具事件审计。
- Claude Code 风格文件工具：
  - `LS`
  - `Read`
  - `Glob`
  - `Grep`
  - `Write`
  - `Edit`
  - `WebSearch`

当前边界：

- 命令执行沙箱还没有开始。
- Bash 还没有开放。
- 跨 workspace、敏感文件和危险操作后续进入权限治理。

## 下一步：SessionKey / AgentState

下一步学习目标：

```text
理解两种状态恢复：
1. 外部上下文恢复
2. AgentScope 内部 checkpoint 恢复
```

### 外部上下文恢复

平台查询：

- `conversation_messages`
- `conversation_summaries`
- `memory_entries`

然后重新组装 messages 发给模型。

特点：

- 简单。
- 可审计。
- 可控。
- 适合聊天续聊和记忆。
- 不能恢复 agent loop 内部现场。

### 内部 checkpoint 恢复

AgentScope 通过：

- `Session`
- `SessionKey`
- `AgentState`

保存和恢复内部状态。

可能包含：

- 当前迭代次数 `curIter`
- 上下文 `context`
- 工具上下文 `toolContext`
- 权限上下文 `permissionContext`
- pending tool recovery

特点：

- 更像 runtime checkpoint。
- 适合中断后继续。
- 适合后续用户确认后恢复执行。
- 更依赖 AgentScope 内部结构，需要谨慎治理。

## 后续学习顺序

### 6. 运行状态机

要学习和实现：

- `RUNNING`
- `WAITING_USER`
- `WAITING_APPROVAL`
- `RESUMED`
- `FAILED`
- `COMPLETED`

重点问题：

```text
Agent 中断时，到底停在哪一步？
下一次恢复时，从哪里继续？
```

### 7. 权限治理

要学习和实现：

- 工具权限分级。
- 文件权限。
- 命令权限。
- 网络权限。
- 用户确认事件。

策略模型：

```text
allow: 直接执行
ask: 请求用户确认
deny: 直接拒绝
```

### 8. 命令级沙箱

要学习和实现：

- `Bash`
- 命令超时。
- stdout / stderr 流式返回。
- 后台任务。
- 危险命令拦截。
- 工作目录限制。
- 环境变量控制。

### 9. Checkpoint + Pending Action

要学习和实现：

- 工具执行前挂起。
- 用户确认后继续。
- 模型网关断线后恢复。
- pending tool recovery 如何接入。

### 10. 回滚和补偿

要学习和实现：

- 文件变更快照。
- 多文件变更事务。
- run 失败后的补偿策略。
- git / worktree 级隔离。

### 11. 多 Agent / 计划型 Agent

要学习和实现：

- Plan Agent。
- Observe Agent。
- Execute Agent。
- Review Agent。
- 子 Agent 隔离上下文。
- 任务拆解和结果合并。

### 12. 产品化治理

要学习和实现：

- 工具审计。
- 配额和限流。
- 模型 fallback。
- workspace 管理。
- 前端体验打磨。
- 错误诊断和运行报告。

## 当前定位

当前我们处在这里：

```text
Agent Loop 基础 ✅
记忆系统第一轮 ✅
文件工具 + 文件沙箱 ✅
事件流 + 前端可观测 ✅
SessionKey / AgentState ⬅ 下一步
运行状态机
权限治理
命令沙箱
Checkpoint 恢复
多 Agent 编排
```

