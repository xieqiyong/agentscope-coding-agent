<template>
  <div :class="['chat-message', message.role]">
    <!-- 角色标签 -->
    <div class="message-role-label">
      <div :class="['role-badge', message.role]">
        <i :class="message.role === 'user' ? 'pi pi-user' : 'pi pi-bolt'" style="font-size: 0.7rem;"></i>
        <span>{{ message.role === 'user' ? '你' : 'Agent' }}</span>
      </div>
    </div>

    <!-- 消息内容 -->
    <div class="message-body">
      <!-- 思考过程：默认展示状态，不展开完整推理链 -->
      <div v-if="message.role === 'assistant' && thinking" class="thinking-trace">
        <button class="thinking-toggle" type="button" @click="thinkingExpanded = !thinkingExpanded">
          <i :class="['pi', thinking.status === 'thinking' ? 'pi-spin pi-spinner' : 'pi-check-circle']"></i>
          <span class="thinking-title">{{ thinkingTitle }}</span>
          <span v-if="thinkingMeta" class="thinking-meta">{{ thinkingMeta }}</span>
          <i :class="['pi', thinkingExpanded ? 'pi-chevron-down' : 'pi-chevron-right', 'thinking-chevron']"></i>
        </button>
        <div v-if="thinkingExpanded" class="thinking-content">
          <template v-if="thinkingContent">{{ thinkingContent }}</template>
          <template v-else>{{ thinkingPlaceholder }}</template>
        </div>
      </div>

      <!-- 工具调用轨迹 -->
      <div v-if="message.role === 'assistant' && toolCalls.length" class="tool-trace">
        <button class="tool-trace-toggle" type="button" @click="toolsExpanded = !toolsExpanded">
          <i class="pi pi-wrench" style="font-size: 0.72rem;"></i>
          <span class="tool-trace-title">工具调用</span>
          <span :class="['tool-trace-state', toolTraceState]">{{ toolTraceLabel }}</span>
          <span class="tool-trace-count">{{ toolCalls.length }} 个</span>
          <i :class="['pi', toolsExpanded ? 'pi-chevron-down' : 'pi-chevron-right', 'tool-trace-chevron']"></i>
        </button>

        <div v-if="!toolsExpanded" class="tool-trace-compact">
          <span
            v-for="tc in compactToolCalls"
            :key="tc.callId"
            :class="['tool-chip', tc.status]"
          >
            {{ formatToolSignature(tc) }}
          </span>
          <span v-if="toolCalls.length > compactToolCalls.length" class="tool-chip more">
            +{{ toolCalls.length - compactToolCalls.length }}
          </span>
        </div>

        <div v-else class="tool-calls">
          <ToolCallCard
            v-for="tc in toolCalls"
            :key="tc.callId"
            :tool-call="tc"
          />
        </div>
      </div>

      <!-- 代码修改提案 -->
      <ConfirmationCard
        v-if="message.confirmation"
        class="confirmation-inline"
        :confirmation="message.confirmation"
        @review="$emit('reviewConfirmation', $event)"
        @approve="$emit('approveConfirmation', $event)"
        @reject="$emit('rejectConfirmation', $event)"
      />

      <!-- 助手消息：Markdown 渲染 -->
      <div
        v-if="message.role === 'assistant' && hasAssistantContent"
        class="message-content markdown-body"
        v-html="renderedContent"
      ></div>
      <!-- 用户消息：简单格式 -->
      <div v-else-if="message.role === 'user'" class="message-content user-content">{{ message.content }}</div>

      <!-- Streaming cursor -->
      <span v-if="message.isStreaming" class="streaming-cursor">▊</span>

      <!-- 消息操作栏：复制 + 点赞/点踩（仅流式结束后显示） -->
      <div v-if="!message.isStreaming && hasContent" class="message-actions">
        <button class="action-btn" @click="copyMessage" title="复制回答">
          <i :class="copied ? 'pi pi-check' : 'pi pi-copy'" style="font-size: 0.72rem;"></i>
          <span>{{ copied ? '已复制' : '复制' }}</span>
        </button>
        <template v-if="message.role === 'assistant'">
          <button :class="['action-btn', { active: feedback === 'like' }]" @click="toggleFeedback('like')" title="点赞">
            <i class="pi pi-thumbs-up" style="font-size: 0.72rem;"></i>
          </button>
          <button :class="['action-btn', { active: feedback === 'dislike' }]" @click="toggleFeedback('dislike')" title="点踩">
            <i class="pi pi-thumbs-down" style="font-size: 0.72rem;"></i>
          </button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { Marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'
import ToolCallCard from './ToolCallCard.vue'
import ConfirmationCard from './ConfirmationCard.vue'
import type { ChatMessage, ToolCallInfo, Confirmation } from '@/types'

const props = defineProps<{
  message: ChatMessage
}>()

defineEmits<{
  reviewConfirmation: [confirmation: Confirmation]
  approveConfirmation: [confirmation: Confirmation]
  rejectConfirmation: [confirmation: Confirmation]
}>()

const toolsExpanded = ref(false)
const thinkingExpanded = ref(false)
const copied = ref(false)
const feedback = ref<'like' | 'dislike' | null>(null)
let copyTimer: ReturnType<typeof setTimeout> | null = null

onBeforeUnmount(() => {
  if (copyTimer) clearTimeout(copyTimer)
})

// 配置 marked，使用 marked-highlight 扩展实现代码高亮
const marked = new Marked(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code: string, lang: string) {
      if (lang && hljs.getLanguage(lang)) {
        try {
          return hljs.highlight(code, { language: lang }).value
        } catch {
          // 高亮失败则返回原文。
        }
      }
      return hljs.highlightAuto(code).value
    },
  }),
  {
    gfm: true,
    breaks: true,
  },
)

const toolCalls = computed(() => props.message.toolCalls || [])
const compactToolCalls = computed(() => toolCalls.value.slice(0, 3))
const thinking = computed(() => props.message.thinking)
const hasAssistantContent = computed(() => Boolean((props.message.content || '').trim()))
const hasContent = computed(() => Boolean((props.message.content || '').trim()))

const thinkingTitle = computed(() => {
  if (!thinking.value) return ''
  return thinking.value.status === 'thinking' ? '正在思考' : '已完成思考'
})

const thinkingMeta = computed(() => {
  if (!thinking.value) return ''
  if (thinking.value.status === 'thinking') return '分析中'
  if (thinking.value.durationMs && thinking.value.durationMs > 0) {
    return `${Math.max(1, Math.round(thinking.value.durationMs / 1000))} 秒`
  }
  if (thinking.value.chars && thinking.value.chars > 0) {
    return `${thinking.value.chars} chars`
  }
  return ''
})

const thinkingContent = computed(() => (thinking.value?.content || '').trim())

const thinkingPlaceholder = computed(() => {
  if (thinking.value?.omitted) {
    return '模型正在分析上下文、工具结果和下一步动作。'
  }
  return '正在整理下一步。'
})

const toolTraceState = computed(() => {
  if (toolCalls.value.some((tc) => tc.status === 'running')) return 'running'
  if (toolCalls.value.some((tc) => tc.status === 'error')) return 'error'
  return 'completed'
})

const toolTraceLabel = computed(() => {
  const labels: Record<string, string> = {
    running: '运行中',
    completed: '完成',
    error: '出错',
  }
  return labels[toolTraceState.value] || toolTraceState.value
})

// 助手消息的完整 Markdown 渲染
const renderedContent = computed(() => {
  const text = props.message.content || ''
  if (!text.trim()) return ''
  return marked.parse(text) as string
})

// 渲染完毕后给代码块注入复制按钮
onMounted(() => {
  addCopyButtonsToCodeBlocks()
})

import { watch, nextTick } from 'vue'
watch(renderedContent, async () => {
  await nextTick()
  addCopyButtonsToCodeBlocks()
})

/**
 * 给所有 pre>code 代码块加上复制按钮。
 */
function addCopyButtonsToCodeBlocks() {
  const container = document.querySelector(`.chat-message.assistant .markdown-body`)
  if (!container) return

  // 找到当前组件的 markdown-body
  const el = document.querySelectorAll('.chat-message .markdown-body pre')
  el.forEach((pre) => {
    if (pre.querySelector('.code-copy-btn')) return // 已有按钮
    const btn = document.createElement('button')
    btn.className = 'code-copy-btn'
    btn.title = '复制代码'
    btn.innerHTML = '<i class="pi pi-copy" style="font-size:0.7rem;"></i>'
    btn.addEventListener('click', () => {
      const code = pre.querySelector('code')
      const text = code?.textContent || pre.textContent || ''
      navigator.clipboard.writeText(text).then(() => {
        btn.innerHTML = '<i class="pi pi-check" style="font-size:0.7rem;color:#22c55e;"></i>'
        setTimeout(() => {
          btn.innerHTML = '<i class="pi pi-copy" style="font-size:0.7rem;"></i>'
        }, 2000)
      })
    })
    // 确保 pre 是相对定位
    ;(pre as HTMLElement).style.position = 'relative'
    pre.appendChild(btn)
  })
}

/**
 * 复制整段回答内容（纯文本）。
 */
async function copyMessage() {
  try {
    await navigator.clipboard.writeText(props.message.content || '')
    copied.value = true
    if (copyTimer) clearTimeout(copyTimer)
    copyTimer = setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    // 剪贴板权限被拒绝
  }
}

/**
 * 点赞/点踩切换。
 */
function toggleFeedback(type: 'like' | 'dislike') {
  feedback.value = feedback.value === type ? null : type
}

function formatToolSignature(toolCall: ToolCallInfo): string {
  const editSummary = formatEditSummary(toolCall)
  if (editSummary) return editSummary

  const compact = compactToolSignature(toolCall.toolName, toolCall.args || {})
  if (compact) return compact

  const entries = Object.entries(toolCall.args || {})
    .filter(([key]) => key !== '_raw')
    .map(([key, value]) => `${key}=${formatArgValue(value)}`)
  if (entries.length > 0) {
    return `${toolCall.toolName}(${entries.join(', ')})`
  }
  if (toolCall.argsText?.trim()) {
    return `${toolCall.toolName}(${toolCall.argsText.trim()})`
  }
  return `${toolCall.toolName}(...)`
}

function formatEditSummary(toolCall: ToolCallInfo): string {
  if (!['write_file', 'apply_patch', 'Write', 'Edit'].includes(toolCall.toolName)) return ''
  const result = toolCall.result || ''
  const total = result.match(/总变更[:：]\s*\+(\d+)\s*\/\s*-(\d+)/)
  const fileCount = result.match(/已编辑文件[:：]\s*(\d+)/)
  const files = Array.from(result.matchAll(/^- (.+?) \((CREATE|MODIFY|DELETE)\): \+(\d+)\s*\/\s*-(\d+)/gm))

  if (files.length === 1) {
    const [, path, changeType, added, deleted] = files[0]
    const action = changeType === 'CREATE' ? 'Created' : changeType === 'DELETE' ? 'Deleted' : 'Edited'
    return `${action} ${path} (+${added} -${deleted})`
  }

  if (total) {
    const count = fileCount?.[1] || String(files.length || 1)
    return `Edited ${count} ${count === '1' ? 'file' : 'files'} (+${total[1]} -${total[2]})`
  }
  return ''
}

function compactToolSignature(toolName: string, args: Record<string, unknown>): string {
  if (toolName === 'write_file') {
    const path = formatArgValue(args.path)
    const mode = args.writeMode ? `, mode=${formatArgValue(args.writeMode)}` : ''
    return `write_file(path=${path}${mode})`
  }
  if (toolName === 'Write') return `Write(file_path=${formatArgValue(args.file_path)})`
  if (toolName === 'Edit') return `Edit(file_path=${formatArgValue(args.file_path)})`
  if (toolName === 'Read') return `Read(file_path=${formatArgValue(args.file_path)})`
  if (toolName === 'LS') return `LS(path=${formatArgValue(args.path)})`
  if (toolName === 'Glob') return `Glob(pattern=${formatArgValue(args.pattern)})`
  if (toolName === 'Grep') return `Grep(pattern=${formatArgValue(args.pattern)})`
  if (toolName === 'WebSearch') return `WebSearch(query=${formatArgValue(args.query)})`
  if (toolName === 'apply_patch') {
    return 'apply_patch(unifiedDiff)'
  }
  if (toolName === 'propose_file_change') {
    const path = formatArgValue(args.path)
    const type = args.changeType ? `, changeType=${formatArgValue(args.changeType)}` : ''
    return `propose_file_change(path=${path}${type})`
  }
  if (toolName === 'propose_patch') {
    const summary = args.summary ? `summary=${formatArgValue(args.summary)}` : 'unifiedDiff'
    return `propose_patch(${summary})`
  }
  return ''
}

function formatArgValue(value: unknown): string {
  if (typeof value === 'string') return JSON.stringify(value)
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  if (value == null) return 'null'
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}
</script>

<style scoped>
/* ==================== 消息容器 ==================== */
.chat-message {
  padding: var(--spacing-md) 0;
  border-bottom: 1px solid var(--border-color);
}

.chat-message:last-child {
  border-bottom: none;
}

/* 用户消息整体靠右 */
.chat-message.user {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

/* Agent 消息靠左 */
.chat-message.assistant {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

/* ==================== 角色标签 ==================== */
.message-role-label {
  margin-bottom: var(--spacing-sm);
}

.role-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--font-size-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  padding: 2px 0;
}

/* 用户角色标签靠右 */
.chat-message.user .message-role-label {
  text-align: right;
}

.role-badge.user {
  color: var(--accent);
}

.role-badge.assistant {
  color: var(--success);
}

/* ==================== 消息内容 ==================== */
.message-body {
  min-width: 0;
  width: 100%;
}

.message-content {
  font-size: var(--font-size-base);
  line-height: 1.7;
  color: var(--text-primary);
}

/* 用户消息 */
.user-content {
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary);
  background: var(--bg-chat-user);
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-sm);
  border-left: 3px solid var(--accent);
  max-width: 80%;
  text-align: right;
  margin-left: auto;
}

/* Agent 消息 Markdown 限制宽度 */
.markdown-body {
  max-width: 90%;
}

/* ==================== Markdown 排版 ==================== */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin-top: 1.2em;
  margin-bottom: 0.5em;
  font-weight: 600;
  line-height: 1.3;
  color: var(--text-primary);
}

.markdown-body :deep(h1) { font-size: 1.4em; }
.markdown-body :deep(h2) { font-size: 1.25em; }
.markdown-body :deep(h3) { font-size: 1.1em; }

.markdown-body :deep(p) {
  margin: 0.5em 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.5em;
}

.markdown-body :deep(li) {
  margin: 0.2em 0;
}

/* 行内代码 */
.markdown-body :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  color: var(--text-primary);
  padding: 1px 5px;
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 0.85em;
}

/* 代码块 */
.markdown-body :deep(pre) {
  position: relative;
  background: #0d1117;
  color: #c9d1d9;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: var(--spacing-md);
  overflow-x: auto;
  margin: 0.75em 0;
  font-size: 0.85em;
  line-height: 1.5;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
  font-family: var(--font-mono);
  font-size: inherit;
  white-space: pre;
}

/* 代码块右上角复制按钮 */
.markdown-body :deep(.code-copy-btn) {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 28px;
  height: 28px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.08);
  color: #adb5bd;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.2s;
}

.markdown-body :deep(pre:hover .code-copy-btn) {
  opacity: 1;
}

.markdown-body :deep(.code-copy-btn:hover) {
  background: rgba(255, 255, 255, 0.15);
  color: #e4e4e7;
}

/* 引用块 */
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--accent);
  margin: 0.75em 0;
  padding: 0.25em 0.75em;
  color: var(--text-secondary);
  background: var(--bg-hover);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}

/* 表格 */
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0.75em 0;
  font-size: 0.9em;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--border-color);
  padding: 6px 12px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: var(--bg-hover);
  font-weight: 600;
  color: var(--text-primary);
}

.markdown-body :deep(td) {
  color: var(--text-secondary);
}

/* 水平线 */
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border-color);
  margin: 1.2em 0;
}

/* 链接 */
.markdown-body :deep(a) {
  color: var(--accent);
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

/* 图片 */
.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: var(--radius-sm);
}

/* 加粗 */
.markdown-body :deep(strong) {
  color: var(--text-primary);
  font-weight: 600;
}

/* ==================== 消息操作栏 ==================== */
.message-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: var(--spacing-sm);
  opacity: 0;
  transition: opacity 0.2s;
}

.chat-message:hover .message-actions {
  opacity: 1;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.7rem;
  padding: 3px 6px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.15s;
}

.action-btn:hover {
  background: var(--bg-hover);
  color: var(--text-secondary);
}

.action-btn.active {
  color: var(--accent);
}

.action-btn.active:hover {
  color: var(--accent-hover);
}

/* ==================== 工具调用 ==================== */
.thinking-trace {
  width: min(100%, 760px);
  margin-bottom: var(--spacing-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--bg-hover) 76%, transparent);
  overflow: hidden;
}

.thinking-toggle {
  width: 100%;
  min-height: 32px;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  border: none;
  background: transparent;
  color: var(--text-secondary);
  padding: 6px var(--spacing-md);
  cursor: pointer;
  text-align: left;
}

.thinking-toggle:hover {
  background: var(--bg-hover);
}

.thinking-title {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-primary);
}

.thinking-meta {
  font-size: 0.62rem;
  font-weight: 600;
  color: var(--text-muted);
}

.thinking-chevron {
  margin-left: auto;
  font-size: 0.62rem;
  color: var(--text-muted);
}

.thinking-content {
  padding: 0 var(--spacing-md) var(--spacing-sm);
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.6;
  white-space: pre-wrap;
}

.tool-trace {
  width: min(100%, 760px);
  margin-bottom: var(--spacing-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--bg-tool-card) 86%, transparent);
  overflow: hidden;
}

.tool-trace-toggle {
  width: 100%;
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  border: none;
  background: transparent;
  color: var(--text-secondary);
  padding: 6px var(--spacing-md);
  cursor: pointer;
  text-align: left;
}

.tool-trace-toggle:hover {
  background: var(--bg-hover);
}

.tool-trace-title {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-primary);
}

.tool-trace-state,
.tool-trace-count {
  font-size: 0.62rem;
  font-weight: 600;
  color: var(--text-muted);
}

.tool-trace-state.running { color: var(--accent); }
.tool-trace-state.completed { color: var(--success); }
.tool-trace-state.error { color: var(--danger); }

.tool-trace-chevron {
  margin-left: auto;
  font-size: 0.62rem;
  color: var(--text-muted);
}

.tool-trace-compact {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 var(--spacing-md) var(--spacing-sm);
}

.tool-chip {
  max-width: 100%;
  display: inline-block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 3px 7px;
  font-family: var(--font-mono);
  font-size: 0.68rem;
  color: var(--text-secondary);
  background: var(--bg-main);
}

.tool-chip.running { border-color: var(--accent); }
.tool-chip.completed { border-color: rgba(34, 197, 94, 0.35); }
.tool-chip.error { border-color: var(--danger); }
.tool-chip.more { color: var(--text-muted); }

.tool-calls {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  padding: 0 var(--spacing-md) var(--spacing-sm);
}

/* ==================== 流式光标 ==================== */
.streaming-cursor {
  color: var(--accent);
  animation: blink 1s step-end infinite;
  font-size: 0.85em;
}

@keyframes blink {
  50% { opacity: 0; }
}
</style>
