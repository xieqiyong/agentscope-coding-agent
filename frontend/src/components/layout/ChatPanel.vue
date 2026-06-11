<template>
  <div class="chat-panel">
    <div class="chat-messages" ref="messagesContainer">
      <!-- Placeholder when no session -->
      <div v-if="!chatStore.currentSession" class="empty-state">
        <i class="pi pi-send empty-icon"></i>
        <p class="empty-title">开始对话</p>
        <p class="empty-desc">选择一个工作区和会话，或创建新会话开始。</p>
      </div>

      <!-- Message list -->
      <MessageList v-else />
    </div>

    <!-- Chat input -->
    <ChatInput />
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import MessageList from '@/components/chat/MessageList.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const messagesContainer = ref<HTMLElement | null>(null)

// Auto-scroll on new messages
watch(
  () => chatStore.messages.length,
  async () => {
    await nextTick()
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  },
)
</script>

<style scoped>
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-md);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
  text-align: center;
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: var(--spacing-md);
  opacity: 0.3;
}

.empty-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: var(--spacing-sm);
}

.empty-desc {
  font-size: var(--font-size-sm);
  max-width: 360px;
}
</style>
