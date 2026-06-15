<template>
  <div class="message-list">
    <ChatMessage
      v-for="msg in chatStore.messages"
      :key="msg.id"
      :message="msg"
      @review-confirmation="$emit('reviewConfirmation', $event)"
      @approve-confirmation="$emit('approveConfirmation', $event)"
      @reject-confirmation="$emit('rejectConfirmation', $event)"
    />
    <div v-if="chatStore.isStreaming && !lastMessageIsStreaming" class="typing-indicator">
      <span class="dot"></span>
      <span class="dot"></span>
      <span class="dot"></span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ChatMessage from './ChatMessage.vue'
import { useChatStore } from '@/stores/chat'
import type { Confirmation } from '@/types'

defineEmits<{
  reviewConfirmation: [confirmation: Confirmation]
  approveConfirmation: [confirmation: Confirmation]
  rejectConfirmation: [confirmation: Confirmation]
}>()

const chatStore = useChatStore()

const lastMessageIsStreaming = computed(() => {
  const msgs = chatStore.messages
  return msgs.length > 0 && msgs[msgs.length - 1].isStreaming
})
</script>

<style scoped>
.message-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  max-width: 800px;
  margin: 0 auto;
  padding: 0 var(--spacing-lg);
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: var(--spacing-sm) var(--spacing-md);
}

.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: bounce 1.4s infinite ease-in-out both;
}

.dot:nth-child(1) { animation-delay: 0s; }
.dot:nth-child(2) { animation-delay: 0.16s; }
.dot:nth-child(3) { animation-delay: 0.32s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
</style>


