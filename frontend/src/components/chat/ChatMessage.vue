<template>
  <div :class="['chat-message', message.role]">
    <div class="message-avatar">
      <i :class="message.role === 'user' ? 'pi pi-user' : 'pi pi-bolt'" style="font-size: 0.85rem;"></i>
    </div>
    <div class="message-body">
      <div class="message-role">{{ message.role === 'user' ? '你' : 'Agent' }}</div>
      <div class="message-content" v-html="renderedContent"></div>

      <!-- Tool calls -->
      <div v-if="message.toolCalls?.length" class="tool-calls">
        <ToolCallCard
          v-for="tc in message.toolCalls"
          :key="tc.callId"
          :tool-call="tc"
        />
      </div>

      <!-- Streaming cursor -->
      <span v-if="message.isStreaming" class="streaming-cursor">▊</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ToolCallCard from './ToolCallCard.vue'
import type { ChatMessage } from '@/types'

const props = defineProps<{
  message: ChatMessage
}>()

const renderedContent = computed(() => {
  // Simple markdown-like rendering for MVP
  // Phase F will use a proper markdown renderer
  let text = props.message.content || ''

  // Bold
  text = text.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  // Inline code
  text = text.replace(/`([^`]+)`/g, '<code>$1</code>')
  // Line breaks
  text = text.replace(/\n/g, '<br>')

  return text
})
</script>

<style scoped>
.chat-message {
  display: flex;
  gap: var(--spacing-sm);
  max-width: 85%;
}

.chat-message.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.chat-message.assistant {
  align-self: flex-start;
}

.message-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: var(--font-size-sm);
}

.chat-message.user .message-avatar {
  background: var(--accent);
  color: white;
}

.chat-message.assistant .message-avatar {
  background: var(--bg-hover);
  color: var(--text-secondary);
}

.message-body {
  min-width: 0;
}

.message-role {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 2px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.message-content {
  font-size: var(--font-size-base);
  line-height: 1.6;
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-md);
}

.chat-message.user .message-content {
  background: var(--accent);
  color: white;
}

.chat-message.assistant .message-content {
  background: var(--bg-chat-agent);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
}

.message-content :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 1px 4px;
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 0.85em;
}

.chat-message.user .message-content :deep(code) {
  background: rgba(255, 255, 255, 0.2);
}

.tool-calls {
  margin-top: var(--spacing-sm);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.streaming-cursor {
  color: var(--accent);
  animation: blink 1s step-end infinite;
  font-size: 0.9em;
}

@keyframes blink {
  50% { opacity: 0; }
}
</style>
