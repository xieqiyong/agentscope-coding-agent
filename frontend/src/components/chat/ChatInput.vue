<template>
  <div class="chat-input-area">
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
      <div class="input-footer">
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
import { useSse } from '@/composables/useSse'
import { modelConfigApi } from '@/api/modelConfig'

const chatStore = useChatStore()
const workspaceStore = useWorkspaceStore()
const sse = useSse()

const inputText = ref('')
const textareaEl = ref<HTMLTextAreaElement | null>(null)
const isFocused = ref(false)
const currentModelName = ref('')

const canSend = computed(
  () => inputText.value.trim().length > 0 && workspaceStore.currentWorkspace && !chatStore.isStreaming,
)

const disabled = computed(
  () => !workspaceStore.currentWorkspace || chatStore.isStreaming,
)

const placeholder = computed(() => {
  if (!workspaceStore.currentWorkspace) return '请先选择一个工作区...'
  if (chatStore.isStreaming) return 'Agent 正在思考...'
  return '描述你的编码任务...'
})

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
  sse.start({
    workspaceId: Number(ws.id),
    conversationId: chatStore.lastConversationId ?? undefined,
    message: command.message,
    runMode: command.runMode,
    agentId: 1,
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
  border-top: 1px solid var(--border-color);
  background: var(--bg-main);
  padding: var(--spacing-md) var(--spacing-lg);
  flex-shrink: 0;
}

.input-container {
  max-width: 800px;
  margin: 0 auto;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: var(--spacing-sm);
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: var(--spacing-sm) var(--spacing-md);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-wrapper.focused {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent);
}

.chat-textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-family: inherit;
  font-size: var(--font-size-base);
  line-height: 1.5;
  color: var(--text-primary);
  background: transparent;
  max-height: 200px;
  min-height: 24px;
}

.chat-textarea::placeholder {
  color: var(--text-muted);
}

.chat-textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.action-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.action-btn.send {
  background: var(--accent);
  color: white;
}

.action-btn.send:hover:not(:disabled) {
  background: var(--accent-hover);
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
</style>
