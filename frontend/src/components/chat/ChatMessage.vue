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
      <!-- 工具调用轨迹：放在回答上方，默认整体折叠，避免挤占正文阅读空间。 -->
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


      <!-- 代码修改提案：像 Codex/Claude Code 一样作为消息的一部分展示，点击后再打开 Diff。 -->
      <ConfirmationCard
        v-if="message.confirmation"
        class="confirmation-inline"
        :confirmation="message.confirmation"
        @review="$emit('reviewConfirmation', $event)"
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
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
}>()

const toolsExpanded = ref(false)

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
const hasAssistantContent = computed(() => Boolean((props.message.content || '').trim()))

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

function formatToolSignature(toolCall: ToolCallInfo): string {
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

/* 用户消息：靠右、带蓝色左边框 */
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

/* 代码块 - 深色终端风格 */
.markdown-body :deep(pre) {
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

/* ==================== 工具调用 ==================== */
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


