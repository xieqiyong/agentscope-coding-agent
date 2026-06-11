# 03 AgentScope 接入设计

## 版本和方向

当前参考版本：

```text
io.agentscope:agentscope:2.0.0-RC1
```

推荐使用：

```text
ReActAgent
Toolkit
Tool
OpenAIChatModel
streamEvents
MiddlewareBase
```

不推荐把新功能建立在：

```text
io.agentscope.core.hook.Hook
LegacyHookDispatcher
SkillHook
GenericRAGHook
StreamingHook
```

原因：旧 hook 在当前版本中已经标记为 deprecated for removal。

## AgentScope 负责什么

AgentScope 负责：

```text
Agent loop
模型调用
工具调用
事件流输出
中间件扩展点
```

我们自己的平台负责：

```text
workspace
sandbox
memory
confirmation
audit
frontend state
```

## ReActAgent 接入形态

推荐适配器：

```text
AgentScopeRuntimeAdapter implements AgentRuntimePort
```

它负责把平台请求转换为 AgentScope 调用。

伪代码：

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(workspaceTool);
toolkit.registerTool(searchTool);
toolkit.registerTool(patchTool);

OpenAIChatModel model = OpenAIChatModel.builder()
        .baseUrl(baseUrl)
        .modelName(modelName)
        .stream(streamEnabled)
        .build();

ReActAgent agent = ReActAgent.builder()
        .name("coding-agent")
        .sysPrompt(systemPrompt)
        .model(model)
        .toolkit(toolkit)
        .maxIters(maxIters)
        .build();
```

## Event Stream

AgentScope 的重要接口：

```java
Flux<AgentEvent> streamEvents(...)
```

事件包括：

```text
AgentStartEvent
AgentEndEvent
ModelCallStartEvent
ModelCallEndEvent
TextBlockDeltaEvent
ThinkingBlockDeltaEvent
ToolCallStartEvent
ToolCallDeltaEvent
ToolCallEndEvent
ToolResultStartEvent
ToolResultTextDeltaEvent
ToolResultEndEvent
ExceedMaxItersEvent
RequireUserConfirmEvent
RequireExternalExecutionEvent
RequestStopEvent
```

第一版应把这些事件转换成平台事件：

```text
agent_started
model_call_started
model_call_finished
answer_delta
tool_call_started
tool_call_args_delta
tool_result_delta
agent_finished
runtime_warning
confirmation_required
```

## doOnNext 的作用

AgentScope 返回的是 Reactor `Flux`。

```java
agent.streamEvents(inputMessages)
    .doOnNext(traceRecorder::record)
    .collectList()
    .block(timeout);
```

`doOnNext` 不是 AgentScope hook，而是 Reactor 的旁路回调。

适合：

```text
日志
trace
耗时统计
SSE 推送
拼接最终答案
工具调用观察
```

不适合：

```text
权限拦截
重试
降级
状态恢复
```

这些应该放在 Middleware 或平台 runtime governance。

## MiddlewareBase

当前 jar 中可见：

```java
MiddlewareBase.onAgent(...)
MiddlewareBase.onReasoning(...)
MiddlewareBase.onActing(...)
MiddlewareBase.onModelCall(...)
MiddlewareBase.onSystemPrompt(...)
```

它是后续运行治理重点。

可能用途：

```text
onAgent：整次 Agent run 超时、trace、预算控制
onReasoning：模型规划阶段约束
onActing：工具执行阶段权限和确认
onModelCall：模型调用超时、重试、降级
onSystemPrompt：动态注入安全规则、记忆、项目约束
```

第一版可以先不用 Middleware，先用工具内部 sandbox 校验。

第二阶段再把治理下沉到 Middleware。

## Tool 设计

AgentScope Tool 是 Java 方法暴露给模型。

示例：

```java
@Tool(name = "readFile", readOnly = true)
public String readFile(@ToolParam(name = "path") String path) {
    ...
}
```

注意：

```text
readOnly=true 只是工具声明，不等于安全边界。
```

真正的安全边界必须在工具内部和 SandboxPolicy 中实现。

## 确认事件

AgentScope 有：

```text
RequireUserConfirmEvent
UserConfirmResultEvent
ConfirmResult
```

它更适合“工具执行前确认”。

例如：

```text
模型想执行 apply_patch
系统发出确认事件
前端展示 diff
用户批准后继续
```

多方案选择不一定要硬塞进这个事件。可以设计平台自己的：

```text
DecisionRequiredEvent
```

## 外部执行事件

AgentScope 还有：

```text
RequireExternalExecutionEvent
ExternalExecutionResultEvent
```

适合工具在外部系统执行的场景。

例如：

```text
浏览器侧执行某个动作
远程 worker 执行命令
外部沙箱执行测试
```

第一版不需要急着用。

## 接入原则

1. 先用 `streamEvents` 做可观测。
2. 工具必须经过 SandboxPolicy。
3. 修改类工具必须走确认。
4. 不依赖 deprecated Hook。
5. 后续治理再研究 MiddlewareBase。
