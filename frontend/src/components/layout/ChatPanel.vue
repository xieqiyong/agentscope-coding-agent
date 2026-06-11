<template>
  <div class="chat-panel">
    <div class="chat-messages" ref="messagesContainer">
      <!-- No workspace selected -->
      <div v-if="!workspaceStore.hasWorkspace" class="empty-state">
        <i class="pi pi-folder-open empty-icon"></i>
        <p class="empty-title">选择一个工作区</p>
        <p class="empty-desc">从顶部下拉选择已有工作区，或点击 + 注册新工作区，即可开始对话。</p>
      </div>

      <!-- Workspace selected but no session -->
      <div v-else-if="!chatStore.currentSession" class="empty-state">
        <i class="pi pi-send empty-icon"></i>
        <p class="empty-title">开始对话</p>
        <p class="empty-desc">在左侧创建或选择一个会话，或者直接输入任务开始。</p>
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
import { useWorkspaceStore } from '@/stores/workspace'

const chatStore = useChatStore()
const workspaceStore = useWorkspaceStore()
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
