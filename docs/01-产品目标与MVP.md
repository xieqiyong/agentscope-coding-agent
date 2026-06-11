# 01 产品目标与 MVP

## 产品定位

本项目要做的是一个网页版 Coding Agent。

核心体验：

```text
用户打开网页
选择一个本地或服务器工作区
输入开发任务
Agent 读取和搜索代码
Agent 解释方案
Agent 生成 diff
用户审核并确认
系统应用修改
```

它不是普通聊天机器人，也不是单纯 RAG 问答。

它的核心是：

```text
Agent + Workspace + Tools + Sandbox + Memory + User Confirmation
```

## 为什么基于 Java AgentScope

原因：

- 用户主技术栈偏 Java。
- AgentScope Java 版已经能提供 ReActAgent、Tool、Event Stream、MiddlewareBase。
- 可以和 Spring Boot、企业系统、数据库、权限系统更自然集成。
- 可以把 Agent Runtime 学习和真实产品实现结合起来。

## 第一版 MVP 范围

第一版只做“安全可观察的 Coding Agent”。

包含：

```text
1. Workspace 注册
2. 文件列表工具
3. 文件读取工具
4. 代码搜索工具
5. AgentScope ReActAgent 接入
6. SSE 事件流
7. Diff 提议
8. 用户确认
9. 记忆注入
10. 运行耗时统计
```

不包含：

```text
1. 自动执行 shell 命令
2. 自动应用 patch
3. Git 自动提交
4. 多用户权限系统
5. 远程容器沙箱
6. 浏览器 IDE 级编辑器
```

这些放到第二阶段以后。

## 核心用户流程

### 读代码

```text
用户：帮我看一下这个项目的登录流程
Agent：列目录 -> 搜索 login/auth/security -> 读取相关文件 -> 总结流程
```

### 提修改方案

```text
用户：给登录接口加参数校验
Agent：读取 controller/service/dto -> 提出方案 -> 生成 diff -> 等待确认
```

### 应用修改

```text
用户确认 diff
系统检查 patch 是否只写允许路径
系统应用 patch
返回修改结果
```

### 记忆生效

```text
用户：以后这个项目里不要改 generated 目录
系统保存为项目约束记忆
后续 Agent 读取/写入工具都受这个约束影响
```

## MVP 成功标准

第一阶段成功标准：

- Agent 能读取指定 workspace 内文件。
- Agent 能搜索代码。
- Agent 不能读取 workspace 外路径。
- Agent 生成的修改必须以 diff 形式展示。
- 用户未确认前不能修改文件。
- 前端能看到 Agent 运行事件和模型输出流。
- 用户偏好或项目约束能保存并影响后续任务。

## 产品形态

建议前端页面第一版只有四块：

```text
左侧：workspace / session / memory
中间：chat stream
右侧：runtime events / tool calls
底部或弹窗：diff review / confirmation
```

不需要一开始做完整 IDE。

## 非目标

第一版不是要做：

- VS Code 替代品。
- 完整云 IDE。
- 任意命令执行平台。
- 自动化 DevOps 平台。

第一版只解决一个问题：

```text
让 Agent 在受控沙箱里读代码、理解代码、提出修改，并让用户可审查地确认修改。
```
