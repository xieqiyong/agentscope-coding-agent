<template>
  <div :class="['chat-input-area', variant]">
    <div class="input-container">
      <div class="input-wrapper" :class="{ focused: isFocused }">
        <textarea
          ref="textareaEl"
          v-model="inputText"
          class="chat-textarea"
          :placeholder="placeholder"
          :disabled="disabled"
          @keydown="onKeydown"
          @focus="isFocused = true"
          @blur="isFocused = false"
          @input="autoResize"
          rows="1"
        />
        <div class="input-actions">
          <button class="utility-btn add" type="button" title="添加上下文">
            <i class="pi pi-plus"></i>
          </button>
          <div class="action-spacer"></div>
          <button
            :class="['mode-control', { active: multiAgentEnabled }]"
            type="button"
            title="多 Agent 自动编排"
            @click="multiAgentEnabled = !multiAgentEnabled"
          >
            <i class="pi pi-sitemap"></i>
            <span>多 Agent</span>
          </button>
          <button class="model-control" type="button" title="模型">
            <span>{{ displayModelName }}</span>
            <i class="pi pi-chevron-down"></i>
          </button>
          <button class="utility-btn" type="button" title="语音输入">
            <i class="pi pi-microphone"></i>
          </button>
          <button class="utility-btn" type="button" title="工具">
            <i class="pi pi-sliders-h"></i>
          </button>
          <button
            v-if="chatStore.isStreaming"
            class="action-btn stop"
            @click="sse.abort()"
            title="停止生成"
          >
            <i class="pi pi-stop" style="font-size: 0.75rem;"></i>
          </button>
          <button
            v-else
            class="action-btn send"
            :disabled="!canSend"
            @click="send"
            title="发送 (Enter)"
          >
            <i class="pi pi-arrow-up" style="font-size: 0.8rem;"></i>
          </button>
        </div>
      </div>

      <!-- 底部信息栏 -->
      <div v-if="variant !== 'landing'" class="input-footer">
        <div class="footer-left">
          <span v-if="currentModelName" class="model-badge">
            <i class="pi pi-bolt" style="font-size: 0.6rem;"></i>
            {{ currentModelName }}
          </span>
        </div>
        <div class="footer-right">
          <span class="footer-hint">Enter 发送 · Shift+Enter 换行</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useWorkspaceStore } from '@/stores/workspace'
import { useAgentStore } from '@/stores/agent'
import { useSse } from '@/composables/useSse'
import { modelConfigApi } from '@/api/modelConfig'

const chatStore = useChatStore()
const workspaceStore = useWorkspaceStore()
const agentStore = useAgentStore()
const sse = useSse()

withDefaults(defineProps<{
  variant?: 'landing' | 'dock'
}>(), {
  variant: 'dock',
})

const inputText = ref('')
const textareaEl = ref<HTMLTextAreaElement | null>(null)
const isFocused = ref(false)
const currentModelName = ref('')
const multiAgentEnabled = ref(true)

const canSend = computed(
  () => inputText.value.trim().length > 0 && workspaceStore.currentWorkspace && agentStore.currentAgent && !chatStore.isStreaming,
)

const disabled = computed(
  () => !workspaceStore.currentWorkspace || !agentStore.currentAgent || chatStore.isStreaming,
)

const placeholder = computed(() => {
  if (!workspaceStore.currentWorkspace) return '请先选择一个工作区...'
  if (!agentStore.currentAgent) return '请先选择或创建一个 Agent...'
  if (chatStore.isStreaming) return 'Agent 正在思考...'
  return 'How can I help you today?'
})

const displayModelName = computed(() => currentModelName.value || 'Sonnet 5 Medium')

// 加载当前模型名称
onMounted(async () => {
  try {
    const res: any = await modelConfigApi.getDefault()
    const cfg = res.data
    if (cfg?.modelName) {
      currentModelName.value = cfg.modelName
    }
  } catch {
    const stored = localStorage.getItem('coding-agent-model')
    if (stored) currentModelName.value = stored
  }
})

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

async function send() {
  const text = inputText.value.trim()
  if (!text || !canSend.value) return
  const command = parseSlashCommand(text)

  chatStore.addUserMessage(text)
  inputText.value = ''

  await nextTick()
  autoResize()

  // 查默认模型配置
  let modelBaseUrl: string | undefined
  let modelName: string | undefined
  let apiKey: string | undefined
  try {
    const res: any = await modelConfigApi.getDefault()
    const cfg = res.data
    modelBaseUrl = cfg.baseUrl
    modelName = cfg.modelName
    apiKey = cfg.apiKeyCipher
  } catch {
    modelBaseUrl = localStorage.getItem('coding-agent-base-url') || undefined
    modelName = localStorage.getItem('coding-agent-model') || undefined
    apiKey = localStorage.getItem('coding-agent-api-key') || undefined
  }

  const ws = workspaceStore.currentWorkspace!
  const agent = agentStore.currentAgent!
  sse.start({
    workspaceId: Number(ws.id),
    conversationId: chatStore.lastConversationId ?? undefined,
    message: command.message,
    runMode: command.runMode || (multiAgentEnabled.value ? 'AUTO' : 'SINGLE_AGENT'),
    agentId: Number(agent.id),
    userId: '1',
    timeoutSeconds: 86400,
    modelBaseUrl,
    modelName,
    apiKey,
  })
}

function parseSlashCommand(text: string): { message: string; runMode?: string } {
  const trimmed = text.trim()
  if (trimmed.startsWith('/plan')) {
    const task = trimmed.slice('/plan'.length).trim()
    return {
      message: task || trimmed,
      runMode: task ? 'PLAN_ONLY' : undefined,
    }
  }
  return { message: trimmed }
}

function autoResize() {
  const el = textareaEl.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 200) + 'px'
  }
}
</script>

<style scoped>
.chat-input-area {
  background: var(--bg-main);
  padding: var(--spacing-md) var(--spacing-lg);
  flex-shrink: 0;
}

.chat-input-area.dock {
  border-top: 1px solid rgba(223, 216, 204, 0.72);
}

.chat-input-area.landing {
  width: min(100%, 840px);
  padding: 0;
  background: transparent;
}

.input-container {
  max-width: 840px;
  margin: 0 auto;
}

.input-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--spacing-sm);
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 24px;
  padding: 20px 22px 16px;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-shadow: 0 6px 16px rgba(47, 42, 36, 0.075);
}

.input-wrapper.focused {
  border-color: #cfc5b7;
  box-shadow: 0 8px 22px rgba(47, 42, 36, 0.11);
}

.chat-textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  font-family: inherit;
  font-size: var(--font-size-base);
  line-height: 1.5;
  color: var(--text-primary);
  background: transparent;
  max-height: 200px;
  min-height: 54px;
}

.chat-textarea::placeholder {
  color: var(--text-muted);
}

.chat-textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-actions {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.action-spacer {
  flex: 1;
}

.utility-btn,
.model-control,
.mode-control {
  height: 34px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--ink);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.utility-btn {
  width: 34px;
  font-size: var(--font-size-sm);
}

.utility-btn.add {
  font-size: 1rem;
}

.model-control,
.mode-control {
  gap: 8px;
  padding: 0 8px;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
}

.mode-control.active {
  background: var(--ink);
  color: var(--bg-main);
}

.model-control span {
  max-width: 190px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.utility-btn:hover,
.model-control:hover,
.mode-control:hover {
  background: var(--bg-hover);
}

.mode-control.active:hover {
  background: #000;
}

.action-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.action-btn.send {
  background: var(--ink);
  color: white;
}

.action-btn.send:hover:not(:disabled) {
  background: #000;
}

.action-btn.send:disabled {
  opacity: 0.3;
  cursor: not-allowed;
  background: var(--text-muted);
}

.action-btn.stop {
  background: var(--danger);
  color: white;
}

.action-btn.stop:hover {
  background: #dc2626;
}

/* 底部信息栏 */
.input-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--spacing-xs);
  padding: 0 var(--spacing-xs);
}

.model-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.65rem;
  color: var(--text-muted);
  background: var(--bg-hover);
  padding: 2px 8px;
  border-radius: 10px;
  font-family: var(--font-mono);
}

.footer-hint {
  font-size: 0.65rem;
  color: var(--text-muted);
}

.footer-left,
.footer-right {
  display: flex;
  align-items: center;
}

.chat-input-area.dock .input-wrapper {
  border-radius: 18px;
  padding: 12px 14px 10px;
  box-shadow: 0 4px 12px rgba(47, 42, 36, 0.055);
}

.chat-input-area.dock .chat-textarea {
  min-height: 28px;
}

@media (max-width: 760px) {
  .chat-input-area {
    padding: 12px;
  }

  .input-wrapper {
    border-radius: 18px;
    padding: 16px;
  }

  .model-control span {
    max-width: 120px;
  }

  .utility-btn:nth-of-type(3) {
    display: none;
  }
}
</style>
