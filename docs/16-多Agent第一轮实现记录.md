# 多 Agent 第一轮实现记录

这份文档记录多 Agent 学习的第一轮、第二轮和第三轮：

- 第一轮：先做一个只规划、不执行、不改代码的 `/plan` 模式。
- 第二轮：在计划卡片上增加“执行计划”，让 Orchestrator 把计划交给 ExecutorAgent 执行。
- 第三轮：普通输入默认进入 `AUTO`，由 RouterAgent 智能判断下一跳。

目标不是马上把系统拆成很多智能体，而是先把“编排器 + 节点 + 状态 + 事件”的骨架搭起来。这样后续接入 Executor、Reviewer、SubAgent、A2A 协议时，不需要推翻现有 Agent Runtime。

## 当前到底有几个 Agent

第一轮是：

```text
Orchestrator
  ↓
PlannerAgent
```

严格来说：

- `Orchestrator` 不是大模型 Agent，它是平台编排器。
- `PlannerAgent` 是第一个真正的 AgentNode，会调用大模型生成结构化计划。
- `ExecutorAgent` 已经接入第二轮雏形，但第一版复用原来的单体 ReAct Coding Agent 执行能力。
- `ReviewerAgent` 暂时还没有接入，只在计划结构里作为后续执行节点预留。

所以当前不是“多个大模型 Agent 并行协作”，而是“多 Agent 架构骨架的第一步”。

第二轮变成：

```text
Orchestrator
  ↓
PlannerAgent
  ↓
计划卡片
  ↓ 用户点击“执行计划”
Orchestrator
  ↓
ExecutorAgent
```

这里的 `ExecutorAgent` 第一版没有重新实现一套工具系统，而是复用原来的单体 ReAct Coding Agent 能力。

原因是：

- 原来的单体 Agent 已经打通文件工具。
- 原来的单体 Agent 已经接入命令沙箱。
- 原来的单体 Agent 已经接入工具审批。
- 原来的单体 Agent 已经能通过 SSE 输出工具轨迹。

所以第二轮先做“角色拆分和编排入口”，不急着重写执行器。

第三轮变成：

```text
普通用户输入
  ↓
Orchestrator
  ↓
RouterAgent
  ↓
ROUTE_SELECTED
  ↓
DIRECT_ANSWER / SINGLE_AGENT / PLAN_ONLY / PLAN_EXECUTE
```

第三轮的目标是把“用户手动选模式”升级为“系统自动判断流程”。

显式命令仍然优先：

```text
/plan xxx
  -> PLAN_ONLY

计划卡片点击执行
  -> PLAN_EXECUTE

普通输入
  -> AUTO
  -> RouterAgent 判断
```

这不是 A2A。它还是同一个后端进程里的本地多 Agent 编排。

## 为什么先做 PLAN_ONLY

Coding Agent 最危险的地方不是规划，而是执行：

```text
读文件
  ↓
改文件
  ↓
跑命令
  ↓
处理失败、回滚、补偿
```

如果一开始就让 Planner、Executor、Reviewer 全部运行，学习成本会很高，也很难判断问题出在哪里。

所以第一轮只做：

```text
用户输入 /plan xxx
  ↓
进入 PLAN_ONLY 模式
  ↓
Orchestrator 把任务交给 PlannerAgent
  ↓
PlannerAgent 生成结构化计划
  ↓
前端展示计划卡片
  ↓
运行结束
```

这个模式不会执行写入工具，也不会执行 Bash。

## 第二轮为什么先复用旧 Agent 执行

旧的单体 Agent 本质上已经是一个完整执行器：

```text
读文件
  ↓
调用工具
  ↓
修改代码
  ↓
执行命令
  ↓
输出结果
```

第二轮新增的 `ExecutorAgent` 做的是一层角色封装：

```text
ExecutorAgent
  ↓
复用 AgentScopeRuntimeAdapter
  ↓
旧 ReAct Coding Agent
  ↓
工具 / 沙箱 / 审批 / SSE
```

这不是最终形态，但它是很稳的过渡形态。

后续真正拆细时，可以逐步替换：

```text
ExecutorAgent
  ↓
专用 Executor Prompt
  ↓
更窄的工具集合
  ↓
按步骤执行
  ↓
ReviewerAgent 审核
```

## 和 LangGraph 思想的对应关系

这次设计刻意贴近 LangGraph 的基本思想：

```text
State
  ↓
Node
  ↓
Edge / Router
  ↓
Checkpoint
  ↓
Interrupt
```

当前对应关系：

```text
MultiAgentState = State
PlannerNode = Node
MultiAgentOrchestrator = Edge / Router 的雏形
agent_events = 可观测事件与恢复依据
approval_requests = Interrupt / Pending Action 的存储
```

第一轮还没有真正实现复杂 Edge，也没有多节点循环。第一轮只是一条固定边：

```text
Orchestrator -> PlannerAgent -> Finish
```

第二轮增加了另一条固定边：

```text
Orchestrator -> ExecutorAgent -> Finish / WaitingApproval
```

第三轮增加 Router 分支：

```text
Orchestrator -> RouterAgent -> ROUTE_SELECTED
                       ↓
        Direct / Single / PlanOnly / PlanExecute
```

后续可以扩展为：

```text
Orchestrator
  ↓
PlannerAgent
  ↓
ObserveAgent
  ↓
ExecutorAgent
  ↓
ReviewerAgent
  ↓
Finalizer
```

## PlannerAgent 的边界

PlannerAgent 只负责回答一个问题：

```text
这件事应该怎么做？
```

它不能做：

- 不能修改文件。
- 不能执行 Bash。
- 不能声称已经读过代码。
- 不能直接给最终执行结果。

它应该输出：

- 计划标题。
- 一句话摘要。
- 风险等级。
- 步骤列表。
- 每个步骤建议由哪个 Agent 执行。
- 每个步骤可能需要哪些工具。
- 完成标准。
- 是否需要用户确认。

这能避免一个常见问题：模型还没读代码，就直接开始改。

## RouterAgent 的边界

RouterAgent 只负责回答一个问题：

```text
这件事应该走哪条流程？
```

当前可选 route：

```text
DIRECT_ANSWER
SINGLE_AGENT
PLAN_ONLY
PLAN_EXECUTE
```

含义：

- `DIRECT_ANSWER`：通用解释、学习讨论、概念问题，不需要读取工作区。
- `SINGLE_AGENT`：需要读取工作区或调查现象，但用户没有明确要求改代码。
- `PLAN_ONLY`：用户明确要求先给计划、方案、设计，不要执行。
- `PLAN_EXECUTE`：用户要求实现、修复、修改、重构、接入、删除、生成文件或执行编码任务。

当前 `DIRECT_ANSWER` 和 `SINGLE_AGENT` 都会交给原来的单体 ReAct Coding Agent 处理。

原因是原单体 Agent 的 system prompt 已经要求：

```text
通用问题直接回答
依赖项目事实才读取工作区
```

后续如果要进一步收窄，可以增加真正的 `DirectAnswerAgent`，不注册工具，只回答通用问题。

## 前端交互

用户在输入框里输入：

```text
/plan 帮我设计一个会话恢复功能
```

前端会把请求转换成：

```json
{
  "message": "帮我设计一个会话恢复功能",
  "runMode": "PLAN_ONLY"
}
```

用户消息仍然保留原始输入，方便知道这次是 slash command。

后端也会兜底识别 `/plan`，所以即使前端没有传 `runMode`，也能进入计划模式。

## 事件流

第一轮新增了几个事件类型：

```text
AGENT_HANDOFF
ROUTE_SELECTED
PLAN_CREATED
PLAN_STEP_STATUS_CHANGED
```

当前主要使用：

```text
RUN_STARTED
CONTEXT_LOADED
AGENT_HANDOFF
ROUTE_SELECTED
MODEL_CALL_STARTED
MODEL_CALL_FINISHED
PLAN_CREATED
RUN_FINISHED
```

前端收到 `PLAN_CREATED` 后，会把结构化 plan 挂到 assistant 消息上，然后渲染计划卡片。

执行计划时会额外看到：

```text
AGENT_HANDOFF
PLAN_STEP_STATUS_CHANGED
MODEL_CALL_STARTED
TOOL_CALL_STARTED
TOOL_RESULT_DELTA
ANSWER_DELTA
RUN_FINISHED
```

如果执行阶段遇到高风险工具，会进入：

```text
CONFIRMATION_REQUIRED
RUN_STATUS_CHANGED: WAITING_APPROVAL
```

## 刷新恢复

计划卡片不是只靠前端临时状态。

`PLAN_CREATED` 事件会写入 `agent_events`，会话 timeline 接口刷新时会重新扫描 run 下面的事件，把 plan 挂回对应 assistant 消息。

这样刷新页面后，计划卡片依然可以恢复。

## 当前边界

第二轮已经可以从计划卡片触发执行，但仍然有几个边界：

- 计划步骤状态是粗粒度的：执行开始时统一变成 `in_progress`，执行结束时统一变成 `completed` 或 `failed`。
- 如果执行中断在工具审批，计划步骤会保持 `in_progress`，后续需要把审批恢复也带上 plan 上下文。
- 当前没有 ReviewerAgent，执行结果还没有独立审查节点。
- RouterAgent 已接入第一版，但还没有 ReviewerAgent、ObserverAgent 和复杂循环。
- `DIRECT_ANSWER` 仍然复用单体 ReAct Coding Agent，后续可以拆成不注册工具的 DirectAnswerAgent。

## 后续演进

下一步可以按这个顺序扩展：

1. 专用 `ExecutorAgent`：从复用旧单体 Agent，演进为更窄工具集、更强步骤控制的专用执行节点。
2. `ReviewerAgent`：检查 diff、工具结果和风险点。
3. `ObserverAgent`：专门读取代码、日志、配置并产出证据，不负责修改。
4. `Router` 增强：结合 skills、MCP、风险等级、历史效果做更细路由。
5. `SubAgent`：给某类任务开独立上下文，避免污染主会话。
6. `A2A`：把 Agent 间通信抽象成协议，而不是本地方法调用。

核心原则：

```text
先有状态，再有节点。
先有事件，再有 UI。
先有审批，再允许执行。
先能恢复，再扩大自动化范围。
```
