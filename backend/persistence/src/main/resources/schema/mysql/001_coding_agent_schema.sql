-- Coding Agent persistence schema for MySQL 8+
-- 说明：这份脚本是 JPA 之外的参考建表脚本，默认不会自动执行。

CREATE TABLE IF NOT EXISTS workspaces (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL COMMENT '工作区名称',
  root_path VARCHAR(1024) NOT NULL COMMENT '工作区根路径，sandbox 会以它作为安全边界',
  owner_id VARCHAR(64) NOT NULL COMMENT '工作区所有者',
  description VARCHAR(512) NULL COMMENT '工作区说明',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED/ARCHIVED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_workspaces_root_path (root_path),
  KEY idx_workspaces_owner (owner_id)
) COMMENT='工作区';

CREATE TABLE IF NOT EXISTS model_configs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL COMMENT '配置名称',
  provider VARCHAR(64) NOT NULL COMMENT '模型供应商，例如 openai-compatible',
  base_url VARCHAR(512) NOT NULL COMMENT '模型网关地址',
  model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
  api_key_cipher TEXT NULL COMMENT 'API Key 密文，不允许直接返回前端',
  api_key_mask VARCHAR(64) NULL COMMENT 'API Key 脱敏值',
  is_default BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否默认配置',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_model_configs_name (name),
  KEY idx_model_configs_default (is_default)
) COMMENT='模型配置';

CREATE TABLE IF NOT EXISTS agents (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workspace_id BIGINT NOT NULL COMMENT '所属工作区',
  name VARCHAR(128) NOT NULL COMMENT 'Agent 名称',
  description VARCHAR(512) NULL COMMENT 'Agent 描述',
  system_prompt TEXT NULL COMMENT '系统提示词',
  skills_json LONGTEXT NULL COMMENT '绑定的 Skills 配置 JSON',
  mcp_services_json LONGTEXT NULL COMMENT '绑定的 MCP 服务配置 JSON',
  model_config_id BIGINT NULL COMMENT '模型配置 ID',
  max_iterations INT NOT NULL DEFAULT 8 COMMENT '最大循环次数',
  timeout_seconds INT NOT NULL DEFAULT 120 COMMENT '单次执行超时时间',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_agents_workspace (workspace_id),
  KEY idx_agents_model_config (model_config_id)
) COMMENT='Agent 定义';

CREATE TABLE IF NOT EXISTS conversations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workspace_id BIGINT NOT NULL COMMENT '所属工作区',
  agent_id BIGINT NULL COMMENT '绑定的 Agent',
  title VARCHAR(256) NOT NULL COMMENT '会话标题',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/ARCHIVED/DELETED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_conversations_workspace (workspace_id),
  KEY idx_conversations_agent (agent_id)
) COMMENT='会话';

CREATE TABLE IF NOT EXISTS conversation_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL COMMENT '会话 ID',
  role VARCHAR(32) NOT NULL COMMENT '角色：SYSTEM/USER/ASSISTANT/TOOL',
  content LONGTEXT NOT NULL COMMENT '消息正文',
  token_count INT NOT NULL DEFAULT 0 COMMENT 'token 数',
  metadata_json JSON NULL COMMENT '消息扩展信息',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_messages_conversation_time (conversation_id, created_at)
) COMMENT='会话消息，短期记忆的原始来源';

CREATE TABLE IF NOT EXISTS conversation_summaries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL COMMENT '会话 ID',
  from_message_id BIGINT NOT NULL COMMENT '摘要起始消息 ID',
  to_message_id BIGINT NOT NULL COMMENT '摘要结束消息 ID',
  summary LONGTEXT NOT NULL COMMENT '摘要内容',
  token_count INT NOT NULL DEFAULT 0 COMMENT '摘要 token 数',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/SUPERSEDED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_summaries_conversation (conversation_id)
) COMMENT='会话摘要';

CREATE TABLE IF NOT EXISTS agent_runs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trace_id VARCHAR(64) NOT NULL COMMENT '追踪 ID',
  conversation_id BIGINT NOT NULL COMMENT '会话 ID',
  agent_id BIGINT NOT NULL COMMENT 'Agent ID',
  workspace_id BIGINT NOT NULL COMMENT '工作区 ID',
  user_message_id BIGINT NULL COMMENT '触发执行的用户消息',
  status VARCHAR(32) NOT NULL COMMENT 'RUNNING/COMPLETED/FAILED/CANCELLED/WAITING_APPROVAL/TIMEOUT',
  error_message TEXT NULL COMMENT '失败原因',
  input_tokens INT NOT NULL DEFAULT 0 COMMENT '输入 token',
  output_tokens INT NOT NULL DEFAULT 0 COMMENT '输出 token',
  started_at DATETIME NOT NULL COMMENT '开始时间',
  finished_at DATETIME NULL COMMENT '结束时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_agent_runs_trace (trace_id),
  KEY idx_agent_runs_conversation (conversation_id),
  KEY idx_agent_runs_workspace_time (workspace_id, started_at)
) COMMENT='Agent 单次执行记录';

CREATE TABLE IF NOT EXISTS agent_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id BIGINT NOT NULL COMMENT 'Agent 执行 ID',
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
  stage VARCHAR(64) NULL COMMENT '事件阶段',
  content LONGTEXT NULL COMMENT '事件正文',
  metadata_json JSON NULL COMMENT '事件元数据',
  elapsed_ms BIGINT NOT NULL DEFAULT 0 COMMENT '距离执行开始的毫秒数',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_agent_events_run (run_id, id)
) COMMENT='Agent 事件流';

CREATE TABLE IF NOT EXISTS tool_definitions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL COMMENT '工具名称',
  description VARCHAR(512) NULL COMMENT '工具描述',
  tool_type VARCHAR(64) NOT NULL COMMENT '工具类型',
  input_schema_json JSON NULL COMMENT '工具入参 JSON Schema',
  risk_level VARCHAR(32) NOT NULL DEFAULT 'LOW' COMMENT '风险等级',
  enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tool_definitions_name (name)
) COMMENT='工具定义';

CREATE TABLE IF NOT EXISTS tool_calls (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id BIGINT NOT NULL COMMENT 'Agent 执行 ID',
  tool_name VARCHAR(128) NOT NULL COMMENT '工具名称',
  arguments_json JSON NULL COMMENT '工具入参',
  result LONGTEXT NULL COMMENT '工具结果',
  status VARCHAR(32) NOT NULL COMMENT '调用状态',
  risk_level VARCHAR(32) NULL COMMENT '风险等级',
  approval_request_id BIGINT NULL COMMENT '关联确认请求',
  started_at DATETIME NOT NULL COMMENT '开始时间',
  finished_at DATETIME NULL COMMENT '结束时间',
  error_message TEXT NULL COMMENT '失败原因',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_tool_calls_run (run_id)
) COMMENT='工具调用记录';

CREATE TABLE IF NOT EXISTS approval_requests (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id BIGINT NOT NULL COMMENT 'Agent 执行 ID',
  workspace_id BIGINT NOT NULL COMMENT '工作区 ID',
  request_type VARCHAR(64) NOT NULL COMMENT '确认类型',
  title VARCHAR(256) NOT NULL COMMENT '确认标题',
  detail_json JSON NULL COMMENT '确认详情',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '确认状态',
  decided_by VARCHAR(64) NULL COMMENT '决策人',
  decided_at DATETIME NULL COMMENT '决策时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_approval_workspace_status (workspace_id, status),
  KEY idx_approval_run (run_id)
) COMMENT='用户确认请求';

CREATE TABLE IF NOT EXISTS patches (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id BIGINT NOT NULL COMMENT 'Agent 执行 ID',
  workspace_id BIGINT NOT NULL COMMENT '工作区 ID',
  title VARCHAR(256) NULL COMMENT 'patch 标题',
  summary TEXT NULL COMMENT '修改摘要',
  diff_text LONGTEXT NOT NULL COMMENT '统一 diff 内容',
  status VARCHAR(32) NOT NULL DEFAULT 'PROPOSED' COMMENT 'patch 状态',
  approval_request_id BIGINT NULL COMMENT '关联确认请求',
  applied_at DATETIME NULL COMMENT '应用时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_patches_run (run_id),
  KEY idx_patches_workspace (workspace_id)
) COMMENT='Agent 生成的代码修改方案';

CREATE TABLE IF NOT EXISTS patch_files (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  patch_id BIGINT NOT NULL COMMENT 'patch ID',
  file_path VARCHAR(1024) NOT NULL COMMENT '相对工作区的文件路径',
  change_type VARCHAR(32) NOT NULL COMMENT '变更类型',
  old_content_hash VARCHAR(128) NULL COMMENT '旧内容 hash',
  new_content_hash VARCHAR(128) NULL COMMENT '新内容 hash',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_patch_files_patch (patch_id)
) COMMENT='patch 文件明细';

CREATE TABLE IF NOT EXISTS memory_entries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workspace_id BIGINT NOT NULL COMMENT '工作区 ID',
  agent_id BIGINT NULL COMMENT 'Agent ID，为空表示工作区通用记忆',
  user_id VARCHAR(64) NOT NULL COMMENT '用户 ID',
  memory_type VARCHAR(64) NOT NULL COMMENT '记忆类型',
  normalized_key VARCHAR(256) NOT NULL COMMENT '归一化 key',
  content TEXT NOT NULL COMMENT '记忆内容',
  source_conversation_id BIGINT NULL COMMENT '来源会话',
  source_message_id BIGINT NULL COMMENT '来源消息',
  confidence DECIMAL(5,4) NOT NULL DEFAULT 0.0000 COMMENT '置信度',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '记忆状态',
  review_reason TEXT NULL COMMENT '审核或冲突说明',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_memory_scope (workspace_id, user_id, status),
  KEY idx_memory_key (workspace_id, user_id, normalized_key)
) COMMENT='长期记忆';

CREATE TABLE IF NOT EXISTS memory_conflicts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workspace_id BIGINT NOT NULL COMMENT '工作区 ID',
  existing_memory_id BIGINT NOT NULL COMMENT '已有记忆 ID',
  candidate_memory_id BIGINT NOT NULL COMMENT '候选记忆 ID',
  conflict_type VARCHAR(64) NOT NULL COMMENT '冲突类型',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '冲突状态',
  resolution VARCHAR(64) NULL COMMENT '处理结果',
  resolved_at DATETIME NULL COMMENT '解决时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_memory_conflicts_status (workspace_id, status)
) COMMENT='记忆冲突记录';
