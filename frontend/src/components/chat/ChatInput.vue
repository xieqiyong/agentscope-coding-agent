<template>
  <div class="chat-input-area">
    <div class="input-wrapper">
      <textarea
        ref="textareaEl"
        v-model="inputText"
        class="chat-textarea"
        :placeholder="placeholder"
        :disabled="disabled"
        @keydown="onKeydown"
        rows="1"
      />
      <button
        v-if="chatStore.isStreaming"
        class="send-btn stop"
        @click="sse.abort()"
        title="停止"
      >
        <i class="pi pi-stop"></i>
      </button>
      <button
        v-else
        class="send-btn"
        :disabled="!canSend"
        @click="send"
        title="发送 (Enter)"
      >
        <i class="pi pi-send"></i>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useWorkspaceStore } from '@/stores/workspace'
import { useSse } from '@/composables/useSse'

const chatStore = useChatStore()
const workspaceStore = useWorkspaceStore()
const sse = useSse()

const inputText = ref('')
const textareaEl = ref<HTMLTextAreaElement | null>(null)

const canSend = computed(
  () => inputText.value.trim().length > 0 && chatStore.currentSession && !chatStore.isStreaming,
)

const disabled = computed(
  () => !chatStore.currentSession || chatStore.isStreaming,
)

const placeholder = computed(() => {
  if (!workspaceStore.currentWorkspace) return '请先选择一个工作区...'
  if (!chatStore.currentSession) return '创建或选择一个会话...'
  if (chatStore.isStreaming) return 'Agent 正在思考...'
  return '描述你的编码任务...（Enter 发送，Shift+Enter 换行）'
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

  chatStore.addUserMessage(text)
  inputText.value = ''

  await nextTick()
  autoResize()

  // Send via SSE
  sse.start('/api/chat/stream', {
    sessionId: chatStore.currentSession!.id,
    workspaceId: workspaceStore.currentWorkspace!.id,
    content: text,
  })
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
  padding: var(--spacing-md);
  background: var(--bg-panel);
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: var(--spacing-sm);
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: var(--spacing-sm) var(--spacing-md);
  transition: border-color 0.15s;
}

.input-wrapper:focus-within {
  border-color: var(--accent);
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
}

.chat-textarea::placeholder {
  color: var(--text-muted);
}

.chat-textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.send-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent);
  color: white;
  flex-shrink: 0;
  transition: all 0.15s;
}

.send-btn:hover:not(:disabled) {
  background: var(--accent-hover);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.send-btn.stop {
  background: var(--danger);
}

.send-btn.stop:hover {
  background: #dc2626;
}
</style>
