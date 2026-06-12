# Claude Code 工具参考手册

## 文件操作类

### Read - 读取文件
读取本地文件系统中的文件内容。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file_path` | string | ✅ | 文件的绝对路径 |
| `offset` | int | ❌ | 起始行号（默认0，最大 9007199254740991） |
| `limit` | int | ❌ | 读取行数（默认2000，最大 9007199254740991） |
| `pages` | string | ❌ | PDF 页码范围，如 "1-5"、"3"、"10-20"（仅PDF，最多20页/次） |

**支持格式：**
- 代码文件（带行号显示）
- 图片（PNG、JPG 等，直接可视化）
- PDF（通过 `pages` 参数）
- Jupyter Notebook（.ipynb，以单元格+输出形式）

**注意：** 读取目录、不存在的文件、空文件会返回错误。编辑过的文件不需要重新读取验证。

---

### Write - 写入文件
创建新文件或完全覆盖已有文件。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file_path` | string | ✅ | 文件的绝对路径 |
| `content` | string | ✅ | 要写入的内容 |

**注意：** 覆盖未读取过的文件会失败。部分修改应使用 Edit 而非 Write。

---

### Edit - 编辑文件
对文件进行精确的字符串替换。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file_path` | string | ✅ | 文件的绝对路径 |
| `old_string` | string | ✅ | 要替换的原文本（必须完全匹配，包括缩进） |
| `new_string` | string | ✅ | 替换后的文本（必须与 old_string 不同） |
| `replace_all` | boolean | ❌ | 是否替换所有匹配项（默认 false） |

**要求：**
- 调用前必须先 Read 过该文件
- `old_string` 必须精确匹配文件内容（含缩进），且在文件中唯一
- `replace_all: true` 时替换所有匹配项

---

### Glob - 文件模式匹配
按 glob 模式快速搜索文件路径。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pattern` | string | ✅ | glob 模式，如 `**/*.js`、`src/**/*.ts` |
| `path` | string | ❌ | 搜索目录（默认当前工作目录） |

**返回：** 按修改时间排序的匹配文件路径列表。

---

### Grep - 内容搜索
基于 ripgrep 的正则表达式内容搜索。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pattern` | string | ✅ | 正则表达式模式 |
| `path` | string | ❌ | 搜索路径（默认当前目录） |
| `glob` | string | ❌ | 文件过滤，如 `*.js`、`*.{ts,tsx}` |
| `type` | string | ❌ | 文件类型：js、py、rust、go、java 等 |
| `output_mode` | string | ❌ | `content`（匹配行）、`files_with_matches`（路径，默认）、`count`（计数） |
| `-i` | boolean | ❌ | 忽略大小写 |
| `-o` | boolean | ❌ | 仅输出匹配部分 |
| `-n` | boolean | ❌ | 显示行号（默认 true） |
| `-A` / `-B` / `-C` | int | ❌ | 上下文行数 |
| `multiline` | boolean | ❌ | 多行模式（`.` 匹配换行） |
| `head_limit` | int | ❌ | 限制输出条数（默认 250，传 0 不限） |
| `offset` | int | ❌ | 跳过前 N 条 |

---

### NotebookEdit - 编辑 Jupyter Notebook
编辑 .ipynb 文件中的单个单元格。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `notebook_path` | string | ✅ | Notebook 绝对路径 |
| `new_source` | string | ✅ | 单元格新内容 |
| `cell_id` | string | ❌ | 单元格 ID（替换/删除时必填） |
| `edit_mode` | string | ❌ | `replace`（默认）、`insert`、`delete` |
| `cell_type` | string | ❌ | `code`、`markdown`（insert 时必填） |

---

## 终端执行类

### Bash - 执行命令
执行 bash 命令并返回输出。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `command` | string | ✅ | 要执行的命令 |
| `description` | string | ❌ | 命令描述（5-10个字） |
| `timeout` | int | ❌ | 超时毫秒（默认 120000，最大 600000） |
| `run_in_background` | boolean | ❌ | 后台运行（命令会持续运行） |
| `dangerouslyDisableSandbox` | boolean | ❌ | 禁用沙箱（需谨慎） |

**特点：**
- 工作目录在调用间持久化
- Shell 环境变量/函数不持久化
- 支持 `gh` CLI 进行 GitHub 操作
- 不支持交互式 git 命令（如 `git rebase -i`）
- commit 消息会自动添加 `Co-Authored-By` 标记

---

## 搜索与研究类

### WebSearch - 网络搜索
搜索互联网，返回标题和 URL。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `query` | string | ✅ | 搜索查询词 |
| `allowed_domains` | string[] | ❌ | 仅包含指定域名 |
| `blocked_domains` | string[] | ❌ | 排除指定域名 |

**注意：** 当前为美国区域，2026年6月数据。

---

### mcp__web_reader__webReader - 读取网页
抓取并转换网页为大模型友好的输入格式。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `url` | string | ✅ | 网页 URL |
| `timeout` | int | ❌ | 超时秒数（默认 20） |
| `return_format` | string | ❌ | `markdown`（默认）或 `text` |
| `retain_images` | boolean | ❌ | 保留图片（默认 true） |
| `no_cache` | boolean | ❌ | 禁用缓存（默认 false） |
| `no_gfm` | boolean | ❌ | 禁用 GFM（默认 false） |
| `keep_img_data_url` | boolean | ❌ | 保留图片 data URL（默认 false） |
| `with_images_summary` | boolean | ❌ | 包含图片摘要（默认 false） |
| `with_links_summary` | boolean | ❌ | 包含链接摘要（默认 false） |

---

### mcp__4_5v_mcp__analyze_image - 图片分析
使用 AI 视觉模型分析图片。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `imageSource` | string | ✅ | 图片远程 URL（支持 PNG、JPG、JPEG） |
| `prompt` | string | ✅ | 分析提示词 |

**提示词建议：**
- 前端代码复刻：需描述布局结构、配色、组件、交互元素
- 其他任务：清晰描述要从图片中分析/提取/理解的内容

---

## 子代理与编排类

### Agent - 启动子代理
启动一个新的代理来处理复杂任务。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `prompt` | string | ✅ | 代理任务描述 |
| `description` | string | ✅ | 简短描述（3-5个字） |
| `subagent_type` | string | ❌ | 代理类型（见下表） |
| `model` | string | ❌ | 模型覆盖：`sonnet`、`opus`、`haiku` |
| `run_in_background` | boolean | ❌ | 后台运行 |
| `isolation` | string | ❌ | 隔离模式：`worktree`（独立 git 工作树） |

**可用代理类型：**

| 类型 | 说明 |
|------|------|
| `claude` | 默认通用代理，拥有所有工具 |
| `claude-code-guide` | Claude Code / API 使用问答 |
| `Explore` | 只读搜索代理，快速广度搜索 |
| `general-purpose` | 通用研究代理，多步骤任务 |
| `Plan` | 架构设计代理，制定实施方案 |
| `statusline-setup` | 配置状态栏 |

---

### Workflow - 多代理工作流
编排多个子代理的确定性工作流脚本。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `script` | string | ❌ | 内联工作流脚本 |
| `name` | string | ❌ | 预定义工作流名称 |
| `scriptPath` | string | ❌ | 脚本文件路径 |
| `args` | any | ❌ | 传递给脚本的参数 |
| `resumeFromRunId` | string | ❌ | 恢复之前运行的 ID |

**脚本内置函数：**
- `agent(prompt, opts)` — 启动子代理
- `pipeline(items, stage1, stage2, ...)` — 流水线（无屏障，各 item 独立推进）
- `parallel(thunks)` — 并行执行（屏障，等待全部完成）
- `phase(title)` — 开始新阶段
- `log(message)` — 输出进度消息
- `workflow(nameOrRef, args)` — 运行子工作流

**限制：**
- 并发代理上限：min(16, cpu cores - 2)
- 单个工作流代理总数上限：1000
- 单次 parallel/pipeline 最多 4096 项

---

## 任务管理类

### TaskCreate - 创建任务
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `subject` | string | ✅ | 任务标题（祈使句） |
| `description` | string | ✅ | 任务详情 |
| `activeForm` | string | ❌ | 进行中显示文本 |

### TaskUpdate - 更新任务
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `taskId` | string | ✅ | 任务 ID |
| `status` | string | ❌ | `pending`、`in_progress`、`completed`、`deleted` |
| `subject` / `description` / `activeForm` | string | ❌ | 更新字段 |
| `owner` | string | ❌ | 指定代理 |
| `addBlocks` | string[] | ❌ | 阻塞的任务 ID |
| `addBlockedBy` | string[] | ❌ | 被阻塞的任务 ID |
| `metadata` | object | ❌ | 附加元数据 |

### TaskList - 列出所有任务
列出任务列表中所有任务的摘要。

### TaskGet - 获取任务详情
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `taskId` | string | ✅ | 任务 ID |

---

## 定时与调度类

### CronCreate - 创建定时任务
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `cron` | string | ✅ | Cron 表达式（本地时区，5字段） |
| `prompt` | string | ✅ | 触发时执行的提示词 |
| `recurring` | boolean | ❌ | 是否周期性（默认 true） |
| `durable` | boolean | ❌ | 持久化到磁盘（默认 false） |

**Cron 格式：** `分 时 日 月 周`
- `*/5 * * * *` — 每5分钟
- `0 9 * * 1-5` — 工作日9点
- `30 14 28 2 *` — 2月28日14:30（一次性）

**注意：** 周期任务7天后自动过期。持久化（durable: true）需要用户明确要求。

### CronDelete - 删除定时任务
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | ✅ | CronCreate 返回的 ID |

### CronList - 列出定时任务
列出所有已创建的定时任务。

---

### ScheduleWakeup - 动态循环唤醒
在 /loop 动态模式中安排下次唤醒。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `delaySeconds` | int | ✅ | 延迟秒数（范围 60-3600） |
| `reason` | string | ✅ | 延迟原因（一句话） |
| `prompt` | string | ✅ | 唤醒时执行的提示词 |

---

## 交互类

### AskUserQuestion - 向用户提问
当决策真正属于用户时使用（无法从代码或常识中推断）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `questions` | array | ✅ | 1-4 个问题（含选项、标题、是否多选） |
| `answers` | object | ❌ | 预设答案 |
| `annotations` | object | ❌ | 附加注释 |
| `metadata` | object | ❌ | 元数据 |

**每个问题结构：**
- `question` — 完整问题
- `header` — 短标签（最多12字符）
- `options` — 2-4 个选项（label + description + 可选 preview）
- `multiSelect` — 是否多选

---

### EnterPlanMode - 进入规划模式
进入规划模式，先探索代码库再设计实施方案。

**无需参数。** 适用于：
- 新功能实现
- 多方案选择
- 影响现有行为的代码修改
- 架构决策

### ExitPlanMode - 退出规划模式
完成规划后提交方案等待用户审批。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `allowedPrompts` | array | ❌ | 实施时需要的权限类别 |

---

### Skill - 调用技能
调用内置的斜杠命令/技能。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `skill` | string | ✅ | 技能名称（不带 `/`） |
| `args` | string | ❌ | 技能参数 |

**可用技能：**

| 技能名 | 说明 |
|--------|------|
| `deep-research` | 深度研究（多源搜索、事实核查、综合报告） |
| `update-config` | 配置 settings.json（权限、环境变量、钩子） |
| `keybindings-help` | 自定义快捷键 |
| `verify` | 验证代码变更是否正常工作 |
| `code-review` | 代码审查（正确性、简化、效率） |
| `simplify` | 简化和优化代码 |
| `fewer-permission-prompts` | 减少权限提示 |
| `loop` | 周期性运行任务 |
| `claude-api` | Claude API 参考 |
| `run` | 启动并运行项目 |
| `init` | 初始化 CLAUDE.md |
| `review` | 审查 Pull Request |
| `security-review` | 安全审查 |

---

## Git 工作树类

### EnterWorktree - 进入工作树
创建隔离的 git 工作树并切换会话到其中。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | ❌ | 新工作树名称（与 path 互斥） |
| `path` | string | ❌ | 已有工作树路径（与 name 互斥） |

### ExitWorktree - 退出工作树
退出工作树会话，返回原始目录。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | ✅ | `keep`（保留）或 `remove`（删除） |
| `discard_changes` | boolean | ❌ | 强制丢弃未提交更改（仅 remove 时） |

---

## 后台任务类

### TaskOutput - 获取任务输出
获取后台运行任务的输出。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `task_id` | string | ✅ | 任务 ID |
| `block` | boolean | ❌ | 是否等待完成（默认 true） |
| `timeout` | int | ❌ | 等待超时毫秒（默认 30000，最大 600000） |

### TaskStop - 停止任务
终止正在运行的后台任务。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `task_id` | string | ✅ | 任务 ID |
