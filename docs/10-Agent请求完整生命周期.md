# 10 Agent 请求完整生命周期

## 这篇文档解决什么

这篇文档记录一次用户请求从前端发起，到 AgentScope 执行、Redis checkpoint、数据库落库、前端刷新恢复的完整生命周期。

核心目标是回答面试里常见的几个问题：

```text
1. 用户消息怎么存？
2. Agent 一次执行怎么建模？
3. 工具调用和模型流式事件怎么存？
4. Redis checkpoint 存什么，为什么不直接存数据库？
5. 前端刷新后怎么恢复聊天和工具轨迹？
6. 多用户、多租户应该怎么扩展？
```

一句话总结：

```text
MySQL 负责用户可见事实和审计，Redis 负责 AgentScope 内部 checkpoint。
```

## 状态分层

当前设计把 Agent 状态拆成三层。

### 1. 产品状态

产品状态落 MySQL。

它包括：

```text
workspace
agent 配置
conversation
message
run
event
patch
approval
memory
```

特点：

```text
可查询
可审计
可回放
可被前端刷新恢复
可以长期保存
```

### 2. 运行事件

运行事件也落 MySQL，但和普通消息分开。

它包括：

```text
RUN_STARTED
CONTEXT_LOADED
MODEL_CALL_STARTED
MODEL_CALL_FINISHED
THINKING_STARTED
ANSWER_DELTA
TOOL_CALL_STARTED
TOOL_CALL_ARGS_DELTA
TOOL_RESULT_DELTA
RUN_STATUS_CHANGED
RUN_FINISHED
RUN_ERROR
```

特点：

```text
粒度细
适合审计和排障
可以重建工具调用轨迹
不直接作为聊天气泡正文
```

### 3. AgentScope 内部 checkpoint

checkpoint 存 Redis。

它包括 AgentScope 自己的内部状态：

```text
AgentState
curIter
context
summary
replyId
toolContext
permissionContext
tasksContext
planModeContext
shutdownInterrupted
```

特点：

```text
运行时状态
变化频繁
不要求用户直接阅读
适合快速恢复 Agent loop
适合后续 pending tool recovery
```

## Redis checkpoint

当前使用 AgentScope 自带的 RedisSession。

平台侧只负责提供：

```text
Session
SessionKey
Redis 配置
```

AgentScope 负责具体保存内部 State。

### SessionKey

当前 sessionKey 设计：

```text
workspace:{workspaceId}:agent:{agentId}:conversation:{conversationId}:user:{userId}
```

设计原因：

```text
workspaceId:
  不同项目不能串上下文。

agentId:
  不同 Agent 配置不能串状态。

conversationId:
  不同会话不能串状态。

userId:
  为多用户隔离预留。
```

### 第一次请求

第一次进入某个 sessionKey：

```text
Redis 中没有 AgentState
  -> stateExists=false
  -> inputStrategy=DATABASE_HISTORY_BOOTSTRAP
  -> 后端把数据库历史消息传给 AgentScope
  -> AgentScope 跑完后把内部 AgentState 写入 Redis
```

### 后续请求

后续同一个 sessionKey：

```text
Redis 中已有 AgentState
  -> stateExists=true
  -> inputStrategy=CURRENT_MESSAGE_ONLY
  -> 后端只把当前用户消息传给 AgentScope
  -> AgentScope 从 Redis 恢复内部上下文
```

这样做是为了避免重复上下文：

```text
错误方式：
  数据库历史传一遍
  Redis AgentState 又恢复一遍
  模型看到重复上下文

当前方式：
  首次用数据库历史引导
  后续用 Redis checkpoint 恢复
```

## 数据库表主线

当前数据库主线：

```text
workspaces
  -> agents
  -> conversations
      -> conversation_messages
      -> agent_runs
          -> agent_events
          -> patches / patch_files
          -> approval_requests
          -> tool_calls
```

### workspaces

工作区表。

它定义 Agent 可以操作的项目根目录。

核心字段：

```text
root_path
owner_id
status
```

设计理念：

```text
workspace 是 sandbox 的安全边界。
所有文件工具都必须把路径限制在 root_path 内。
```

### agents

Agent 定义表。

它保存 Agent 配置，不保存某次执行状态。

核心字段：

```text
workspace_id
system_prompt
model_config_id
max_iterations
timeout_seconds
status
```

设计理念：

```text
agents 是 Agent 模板。
agent_runs 才是某次实际运行。
```

### model_configs

模型配置表。

核心字段：

```text
provider
base_url
model_name
api_key_cipher
api_key_mask
is_default
```

设计理念：

```text
模型配置独立出来，避免每个 Agent 重复保存 baseUrl/model/apiKey。
```

注意：

```text
api_key_cipher 当前是字段设计，后续应接真正的加密和解密服务。
```

### conversations

会话表。

核心字段：

```text
workspace_id
agent_id
title
status
```

设计理念：

```text
conversation 是产品层会话。
前端左侧会话列表查它。
Redis checkpoint 不替代它。
```

### conversation_messages

会话消息表。

核心字段：

```text
conversation_id
role
content
token_count
metadata_json
```

设计理念：

```text
只存用户和助手最终可见消息。
不把每一个工具 delta 都塞进 message 表。
```

当前主要角色：

```text
USER
ASSISTANT
```

未来可以扩展：

```text
SYSTEM
TOOL
```

### agent_runs

Agent 单次执行表。

一条用户消息会触发一次 run。

核心字段：

```text
trace_id
conversation_id
agent_id
workspace_id
user_message_id
status
input_tokens
output_tokens
started_at
finished_at
error_message
```

设计理念：

```text
conversation 解决“这是哪段聊天”。
agent_run 解决“这条消息触发的一次 Agent loop 跑得怎么样”。
```

常见状态：

```text
RUNNING
COMPLETED
FAILED
WAITING_APPROVAL
TIMEOUT
CANCELLED
```

### agent_events

Agent 事件流表。

核心字段：

```text
run_id
event_type
stage
content
metadata_json
elapsed_ms
```

设计理念：

```text
agent_events 是原始运行事件流。
它负责审计、排障、回放和前端刷新后的工具轨迹重建。
```

它比 conversation_messages 更细。

例如：

```text
conversation_messages:
  ASSISTANT: 最终回答文本

agent_events:
  THINKING_STARTED
  TOOL_CALL_STARTED
  TOOL_CALL_ARGS_DELTA
  TOOL_RESULT_DELTA
  ANSWER_DELTA
```

### tool_calls

工具调用结构化表。

核心字段：

```text
run_id
tool_name
arguments_json
result
status
risk_level
approval_request_id
started_at
finished_at
error_message
```

当前定位：

```text
表已经设计好。
当前主链路主要依赖 agent_events 重建工具轨迹。
后续可以把 tool_calls 做成 agent_events 的结构化投影表。
```

面试表达：

```text
agent_events 是 event sourcing 原始流。
tool_calls 是工具调用物化视图，方便查询和统计。
```

### patches / patch_files

代码修改方案表。

`patches` 保存 diff：

```text
run_id
workspace_id
summary
diff_text
status
approval_request_id
applied_at
```

`patch_files` 保存文件明细：

```text
patch_id
file_path
change_type
old_content_hash
new_content_hash
```

设计理念：

```text
Agent 生成修改方案。
平台保存 diff。
用户确认后再应用。
```

### approval_requests

用户确认请求表。

核心字段：

```text
run_id
workspace_id
request_type
title
detail_json
status
decided_by
decided_at
```

设计理念：

```text
高风险工具不能直接执行。
必须进入 ask/approve/reject 流程。
```

未来适合：

```text
apply_patch 确认
Bash 命令确认
跨目录访问确认
网络访问确认
```

### conversation_summaries

会话摘要表。

当前属于预留和后续治理。

设计理念：

```text
几十轮会话后，不能无限把原始消息塞给模型。
需要把早期消息压缩成 summary。
```

### memory_entries / memory_conflicts

长期记忆表和冲突表。

这部分属于后续记忆模块，本篇只说明它们在生命周期中的位置：

```text
运行前：
  active memory 注入上下文

运行后：
  从消息中抽取 candidate memory
  审核后进入 active
```

## 一次请求的完整写入时机

### 1. 前端发起请求

前端调用：

```text
POST /api/agent-runtime/chat-stream
```

携带：

```text
workspaceId
agentId
conversationId
userId
message
modelBaseUrl
modelName
apiKey
timeoutSeconds
```

如果是新会话，可以没有 conversationId。

### 2. 创建或加载 conversation

后端判断：

```text
如果 conversationId 存在：
  查询 conversations

如果 conversationId 不存在：
  创建 conversations
```

写入表：

```text
conversations
```

### 3. 保存用户消息

用户消息先落库。

写入表：

```text
conversation_messages
```

字段：

```text
conversation_id
role=USER
content=用户输入
token_count=估算 token
metadata_json={}
```

这样即使后续 Agent 失败，用户发过什么也不会丢。

### 4. 创建 agent_run

创建一次运行记录。

写入表：

```text
agent_runs
```

字段：

```text
trace_id
conversation_id
agent_id
workspace_id
user_message_id
status=RUNNING
started_at
```

### 5. 推送并记录 RUN_STARTED

后端生成平台事件：

```text
RUN_STARTED
```

同时做两件事：

```text
1. SSE 推给前端
2. 写 agent_events
```

### 6. 构建 RuntimeContext

后端加载：

```text
workspace
agent
model_config
conversation_messages
active memory
```

组装：

```text
systemPrompt
recentMessages
model config
workspace context
memory context
```

然后发出：

```text
CONTEXT_LOADED
```

写入表：

```text
agent_events
```

### 7. 绑定 Redis checkpoint

AgentScopeRuntimeAdapter 创建 ReActAgent 前绑定：

```text
Session
SessionKey
```

检查：

```text
session.exists(sessionKey)
```

然后发出：

```text
CONTEXT_LOADED
stage=AgentScope 状态加载完成
metadata:
  enabled
  type
  stateExists
  sessionKey
  inputStrategy
```

写入表：

```text
agent_events
```

Redis 写入时机：

```text
AgentScope 在运行过程中或结束时保存内部 AgentState。
```

### 8. AgentScope 执行 Agent Loop

AgentScope 开始：

```text
模型调用
工具调用
工具结果
再次模型调用
最终回答
```

每个 AgentScope event 会被翻译成 RuntimeEvent。

每个 RuntimeEvent 都会：

```text
1. 先通过 SSE 发给前端
2. 再写入 agent_events
```

当前代码里事件落库失败不会中断主链路。

原因：

```text
SSE 是用户体验主链路。
事件落库是审计链路。
审计失败不能拖垮用户请求。
```

### 9. 保存助手最终消息

AgentScope 完成后，后端拿到最终 answer。

写入表：

```text
conversation_messages
```

字段：

```text
conversation_id
role=ASSISTANT
content=最终回答
token_count=估算或真实 token
metadata_json={}
```

### 10. 更新 agent_run 完成状态

更新表：

```text
agent_runs
```

字段：

```text
status=COMPLETED
input_tokens
output_tokens
finished_at
```

这里的状态修改不再直接散落调用 `setStatus`，而是统一经过运行生命周期服务。

状态机负责保证：

```text
RUNNING / WAITING_APPROVAL 才能进入终态
COMPLETED / FAILED / TIMEOUT / CANCELLED 进入后不能再被晚到事件覆盖
```

### 11. 推送 RUN_FINISHED

发出事件：

```text
RUN_FINISHED
```

metadata 包括：

```text
inputTokens
outputTokens
modelCallCount
conversationId
```

前端用 conversationId 记录续聊 ID。

## 异常路径

如果 AgentScope 执行失败：

```text
agent_runs.status=FAILED
agent_runs.error_message=异常信息
```

如果异常链路识别为超时：

```text
agent_runs.status=TIMEOUT
agent_runs.error_message=超时异常信息
```

然后发出：

```text
RUN_ERROR
```

并写入：

```text
agent_events
```

用户消息和 run 记录不会丢。

这对排障很重要：

```text
可以查到用户问了什么
可以查到 Agent 跑到哪一步失败
可以查到 trace_id
可以查到错误信息
```

## 前端刷新恢复

前端刷新不能靠 Redis。

刷新恢复走：

```text
POST /api/sessions/{id}/timeline
```

后端查询：

```text
conversation_messages
agent_runs
agent_events
```

恢复逻辑：

```text
1. conversation_messages 还原用户和助手消息。
2. agent_runs 找到每次运行。
3. agent_events 里筛 TOOL_* 事件。
4. 按 toolCallId / callId 重建 toolCalls。
5. 把 toolCalls 挂到对应助手消息上。
```

所以：

```text
页面展示 = MySQL
Agent 内部恢复 = Redis
```

## 多租户和用户设计

当前项目是 MVP，只有简化字段：

```text
workspaces.owner_id
memory_entries.user_id
AgentRunCommand.userId
```

正式生产系统应增加：

```text
tenant_id
user_id
```

建议加入这些表：

```text
workspaces
agents
conversations
conversation_messages
agent_runs
agent_events
approval_requests
patches
memory_entries
```

查询必须带作用域：

```text
tenant_id + workspace_id
tenant_id + user_id
tenant_id + conversation_id
```

避免：

```text
用户 A 查到用户 B 的会话
项目 A 恢复到项目 B 的 checkpoint
租户 A 的 Agent 使用租户 B 的模型配置
```

Redis sessionKey 也应包含 tenant：

```text
tenant:{tenantId}:workspace:{workspaceId}:agent:{agentId}:conversation:{conversationId}:user:{userId}
```

当前 MVP 没加 tenant，是为了降低学习复杂度。

## 为什么不只用一种存储

### 只用 MySQL 的问题

如果所有 AgentState 都落 MySQL：

```text
字段结构强依赖 AgentScope 内部实现
写入频繁
恢复成本高
不适合快速 checkpoint
```

### 只用 Redis 的问题

如果聊天记录也只放 Redis：

```text
页面历史不稳定
审计能力弱
长期保存困难
不适合复杂查询
```

所以当前采用：

```text
MySQL:
  业务事实、消息、审计、回放。

Redis:
  AgentScope 内部 checkpoint。
```

## 面试表达

可以这样说：

```text
一次 Agent 请求进入后，我们先保证用户输入和 run 记录落库。
这样即使后面模型或工具失败，也能审计用户触发了什么。

随后创建 RuntimeContext，加载 workspace、agent、模型配置、历史消息和记忆。
在调用 AgentScope 前，我们用 workspaceId、agentId、conversationId、userId 构造 sessionKey，绑定 RedisSession。

如果 Redis 中没有 AgentState，说明这是该 sessionKey 第一次运行，就用数据库历史消息 bootstrap。
如果 Redis 中已经有 AgentState，说明 AgentScope 可以恢复内部上下文，本轮只传当前用户消息，避免重复上下文。

运行过程中，AgentScope event 会转换成平台 RuntimeEvent。
这些事件一边通过 SSE 推给前端，一边写入 agent_events。
最终回答写入 conversation_messages，run 更新为 COMPLETED。

前端刷新时不依赖 Redis，而是查 conversation_messages、agent_runs、agent_events 重新组装 timeline。
Redis 只负责 Agent 内部 checkpoint，MySQL 负责用户可见历史和审计。
```

一句话：

```text
数据库解决“发生了什么”，Redis 解决“Agent 内部跑到哪里了”。
```

## 当前边界

当前已经有：

```text
conversation
message
run
event
Redis checkpoint
RUNNING / WAITING_APPROVAL / COMPLETED / FAILED / TIMEOUT / CANCELLED 基础状态机
timeline 恢复
patch 方案
基础 approval 表
```

当前还没有完整实现：

```text
租户 tenant_id
tool_calls 物化写入
approval 后恢复执行
approval_requests 和 WAITING_APPROVAL 的强绑定
Redis TTL 和清理
summary 压缩治理
多实例并发 sessionKey 锁
```

这些是后续运行治理和记忆模块要继续补的部分。
