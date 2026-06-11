<template>
  <div class="message-list">
    <ChatMessage
      v-for="msg in chatStore.messages"
      :key="msg.id"
      :message="msg"
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
  gap: var(--spacing-md);
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: var(--spacing-sm) var(--spacing-md);
}

.dot {
  width: 6px;
  height: 6px;
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
