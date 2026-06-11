# Persistence 模块

这个模块使用 JPA，不使用 MyBatis。

## 模块职责

- 保存 coding agent 的核心持久化模型。
- 提供 Spring Data JPA Repository。
- 不写业务编排，不直接执行 AgentScope，不直接做 sandbox 决策。

## 表结构主线

```text
workspaces
-> conversations / conversation_messages / conversation_summaries
-> agent_runs / agent_events
-> tool_definitions / tool_calls
-> approval_requests
-> patches / patch_files
-> memory_entries / memory_conflicts
```

## JPA 初始化

开发环境在 `bootstrap/src/main/resources/application.yml` 中使用 H2 MySQL Mode，并通过：

```yaml
spring.jpa.hibernate.ddl-auto: update
```

自动初始化表。

生产或本地 MySQL 配置在 `application-prod.yml`，学习阶段也先用 `update`，后续稳定后建议改成 Flyway 或 Liquibase。

## SQL

`src/main/resources/schema/mysql/001_coding_agent_schema.sql` 是 MySQL 参考建表脚本，不会被默认自动执行。
