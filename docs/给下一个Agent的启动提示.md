# 给下一个 Agent 的启动提示

你正在接手一个新项目：

```text
E:\ai-work\java-agentscope-coding-agent
```

这个项目来自上一个 `ragmem` 学习会话。

## 必读上下文

请先读取：

```text
AGENTS.md
README.md
docs/00-项目交接说明.md
docs/01-产品目标与MVP.md
docs/02-架构设计.md
docs/03-AgentScope接入设计.md
docs/04-沙箱与工具治理设计.md
docs/05-记忆系统设计.md
docs/06-实施路线图.md
```

不要直接凭空实现。

## 项目目标

做一个网页版 Coding Agent，类似 opencode 的交互体验，但基于 Java AgentScope：

```text
Java AgentScope
Spring Boot
SSE streaming
Tool calling
Workspace sandbox
User confirmation
Memory system
Diff review
```

## 当前重要设计判断

1. AgentScope 是 runtime，不是整个平台。
2. 记忆系统由我们自己管理，不写死到 AgentScope 内部。
3. AgentScope deprecated Hook 不作为新设计基础。
4. 观测使用 `streamEvents`。
5. 后续治理研究 `MiddlewareBase`。
6. 工具执行必须经过 SandboxPolicy。
7. 写文件和执行命令必须先确认。
8. 第一版只做 read-only 工具和 patch proposal。

## 第一阶段不要做的事

不要一开始做：

```text
自动执行 shell
自动写文件
git commit
完整 IDE
复杂多租户权限
远程容器沙箱
```

先保证：

```text
workspace 内安全读取
AgentScope 能调用工具
前端能看 SSE
工具调用过程可观测
```

## 推荐开始命令

如果项目还没有代码骨架，先创建 Spring Boot Maven 项目。

推荐包名：

```text
com.example.codingagent
```

推荐 artifact：

```text
java-agentscope-coding-agent
```

推荐首批类：

```text
CodingAgentApplication
WorkspaceController
ChatController
WorkspaceApplicationService
SandboxPathResolver
AgentScopeRuntimeAdapter
ListFilesTool
ReadFileTool
SearchCodeTool
```

## 实现顺序

1. 建后端骨架。
2. 建 workspace 注册 API。
3. 实现路径安全解析。
4. 实现 read-only 文件工具。
5. 接 AgentScope ReActAgent。
6. 接 SSE。
7. 接 runtime event 显示。
8. 再做 patch proposal。
9. 再做记忆。
10. 再做 MiddlewareBase 治理。

## 验收用例

第一个验收用例：

```text
用户选择 workspace
用户问：帮我看这个项目结构
Agent 调用 list_files/read_file/search_code
前端流式展示过程
Agent 总结项目结构
```

第二个验收用例：

```text
用户问：帮我修改某个方法
Agent 读取相关文件
Agent 生成 diff proposal
前端展示 diff
用户确认前不写文件
```

## 风格要求

保持设计清楚，别过早堆功能。

每次新增工具前先写清楚：

```text
工具输入
工具输出
风险等级
沙箱校验
是否需要用户确认
审计字段
```
