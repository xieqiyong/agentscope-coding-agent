# AGENTS.md

This file is the durable entry point for any future coding agent working in this repository.

## First Rule

Before editing code, read these documents in order:

1. `docs/00-项目交接说明.md`
2. `docs/01-产品目标与MVP.md`
3. `docs/02-架构设计.md`
4. `docs/03-AgentScope接入设计.md`
5. `docs/04-沙箱与工具治理设计.md`
6. `docs/05-记忆系统设计.md`
7. `docs/06-实施路线图.md`
8. `docs/给下一个Agent的启动提示.md`

Do not start implementation from memory or assumptions. This project was split out from the `ragmem` learning project, and these docs are the source of continuity.

## Product Target

Build a browser-based Java AgentScope Coding Agent:

- Java backend, preferably Spring Boot.
- AgentScope as the Agent runtime.
- Web UI with SSE streaming.
- Workspace-aware file tools.
- Sandbox and permission governance.
- User confirmation before risky actions.
- Memory system for user preferences, project facts, constraints, and reusable skills.

## Key Architecture Rule

Keep framework runtime and platform capabilities separate:

- AgentScope runs the Agent loop.
- Our platform owns memory, workspace governance, sandbox policy, tool approval, and persistence.
- AgentScope events are observed through `streamEvents`.
- Deprecated AgentScope `core.hook.Hook` should not be the basis of new design.
- Runtime governance should investigate `MiddlewareBase`, not legacy hooks.

## Safety Rules

- Never apply file writes without a reviewable diff and explicit approval path.
- Never run arbitrary shell commands without sandbox policy and user confirmation.
- Never allow path traversal outside an approved workspace root.
- Never store secrets in memory by default.
- Treat generated patches as proposals until approved.

## Development Constraints

- All API endpoints should use POST requests. Do not add new GET, PUT, PATCH, or DELETE endpoints unless the project owner explicitly approves an exception.
- Code comments must be written in Chinese.
- When injecting services, use dependency-injection annotations such as `@Autowired` or `@Resource`. Do not use constructor injection or Lombok constructor-based injection such as `@RequiredArgsConstructor` for service dependencies.

## First Implementation Milestone

Implement an MVP with read-only workspace tools first:

1. Register workspace root.
2. List files safely.
3. Read files safely.
4. Search code safely.
5. Stream AgentScope events to the frontend.
6. Show proposed changes as diff only, without applying them.

Write tools and sandbox checks before enabling mutation.
