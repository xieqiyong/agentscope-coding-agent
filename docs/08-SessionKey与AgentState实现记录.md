# 08 SessionKey 与 AgentState 实现记录

## 本次目标

这一步解决的是 Agent Loop 的内部状态恢复问题。

它和普通聊天历史恢复不是一回事：

```text
数据库会话恢复：
  查询 conversation_messages / memory_entries
  重新组装 messages
  再交给模型

AgentScope 内部状态恢复：
  使用 Session + SessionKey
  恢复 AgentState
  让 ReActAgent 继续维护自己的上下文、迭代状态和工具状态
```

主流做法是两者同时存在，但职责分开。

## 为什么不用数据库替代 AgentState

数据库保存的是平台可审计数据：

```text
conversation_messages
agent_runs
agent_events
tool_calls
memory_entries
```

这些数据适合：

```text
页面刷新
历史会话展示
审计追踪
用户可见记录
长期记忆抽取
```

AgentScope Session 保存的是框架内部状态：

```text
AgentState
curIter
context
toolContext
permissionContext
pending tool recovery
```

这些数据适合：

```text
同一个 Agent Loop 的上下文恢复
工具中断后的继续执行
后续用户确认后的恢复
模型网关异常后的补偿设计
```

所以不要把 AgentScope 的内部状态混进业务会话表，也不要用 Redis 替代业务会话表。

## SessionKey 设计

本次采用稳定、可隔离的 sessionKey：

```text
workspace:{workspaceId}:agent:{agentId}:conversation:{conversationId}:user:{userId}
```

设计原因：

```text
workspaceId:
  不同项目不能串上下文。

agentId:
  同一个项目里的不同 Agent 配置不能串状态。

conversationId:
  同一个 Agent 的不同聊天会话不能串状态。

userId:
  后续多用户协作时，不同用户不能串偏好和权限上下文。
```

这也是面试里最容易讲清楚的部分：状态隔离粒度必须明确，否则恢复能力会变成串上下文风险。

## RedisSession 接入

AgentScope 2.0.0-RC1 已经提供：

```java
RedisSession.builder()
    .lettuceClient(redisClient)
    .keyPrefix(keyPrefix)
    .build();
```

本项目使用 Lettuce 作为 Redis 客户端，因为它是 Spring 生态里常见的 Redis 客户端，也适合做进程级单例。

创建 ReActAgent 时绑定：

```java
ReActAgent.builder()
    .session(session)
    .sessionKey(sessionKey)
    .enablePendingToolRecovery(true)
    .build();
```

`enablePendingToolRecovery(true)` 的意义是为后续“工具执行前挂起、用户确认后继续”留下入口。

## 输入上下文策略

这里最关键的是避免重复上下文。

第一次进入某个 sessionKey：

```text
Redis 中没有 AgentState
  -> 使用数据库历史消息初始化 AgentScope
  -> inputStrategy = DATABASE_HISTORY_BOOTSTRAP
```

后续继续同一个 sessionKey：

```text
Redis 中已经有 AgentState
  -> 本轮只发送当前用户消息
  -> 历史上下文由 AgentScope 从 Session 恢复
  -> inputStrategy = CURRENT_MESSAGE_ONLY
```

如果不做这个判断，会出现：

```text
数据库历史消息塞一遍
AgentScope Session 又恢复一遍
最终模型看到重复上下文
```

这会带来 token 浪费、回答漂移、工具调用重复等问题。

## 配置项

当前配置：

```yaml
agent:
  runtime:
    session:
      enabled: true
      type: redis
      key-prefix: agent-platform:agentscope:session:
      redis:
        uri: redis://127.0.0.1:6379/0
      json:
        path: data/agentscope-sessions
```

`type` 支持：

```text
redis:
  推荐生产和主线学习使用。

json:
  适合没有 Redis 的本地临时验证。

memory:
  只适合单进程临时调试，服务重启后状态丢失。
```

## 运行时事件

绑定完成后会发出一条平台事件：

```text
type: CONTEXT_LOADED
stage: AgentScope 状态加载完成
metadata:
  enabled
  type
  stateExists
  sessionKey
  inputStrategy
```

这样前端和数据库审计都能看到本轮是：

```text
首次引导上下文
还是从 AgentState 恢复
```

## 当前边界

本次只完成 AgentScope 内部状态接入。

还没有做：

```text
Redis TTL
状态清理 API
用户确认后的继续执行协议
模型异常后的 run 恢复状态机
跨进程锁
多实例并发同一个 sessionKey 的互斥控制
```

这些属于后续运行环境治理模块。

## 面试表达

可以这样讲：

```text
我们把 Agent 的状态分成两层。

第一层是平台状态，落数据库，包括会话消息、运行记录、工具事件和审计日志。
它负责可见、可查、可审计。

第二层是 AgentScope 内部状态，使用 RedisSession 按 sessionKey 存 AgentState。
它负责恢复 Agent Loop 内部上下文、工具上下文和后续 pending action。

为了避免重复上下文，我们会先检查 Redis 中是否存在 AgentState。
如果不存在，就用数据库历史消息初始化。
如果已存在，本轮只把当前用户消息传给 AgentScope，让框架自己从 Redis 恢复上下文。
```

一句话总结：

```text
数据库负责产品会话，RedisSession 负责 Agent checkpoint。
```
