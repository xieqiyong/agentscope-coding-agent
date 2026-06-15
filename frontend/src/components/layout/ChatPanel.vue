<template>
  <div class="chat-panel">
    <div class="chat-messages" ref="messagesContainer">
      <!-- 没有选择工作区 -->
      <div v-if="!workspaceStore.hasWorkspace" class="empty-state">
        <div class="empty-logo">
          <i class="pi pi-bolt"></i>
        </div>
        <p class="empty-title">选择一个工作区开始</p>
        <p class="empty-desc">从顶部下拉选择已有工作区，或点击 + 注册新工作区。</p>
      </div>

      <!-- 有工作区但没有消息 -->
      <div v-else-if="chatStore.messages.length === 0 && !chatStore.isStreaming" class="empty-state">
        <div class="empty-logo">
          <i class="pi pi-bolt"></i>
        </div>
        <p class="empty-title">描述你的编码任务</p>
        <p class="empty-desc">Agent 会读取工作区文件、搜索代码，并生成可审查的修改方案。</p>
      </div>

      <!-- 消息列表 -->
      <MessageList
        v-else
        @review-confirmation="$emit('reviewConfirmation', $event)"
        @approve-confirmation="handleToolApproval($event, true)"
        @reject-confirmation="handleToolApproval($event, false)"
      />
    </div>

    <!-- 输入栏 -->
    <ChatInput />
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import MessageList from '@/components/chat/MessageList.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import { useChatStore } from '@/stores/chat'
import { useWorkspaceStore } from '@/stores/workspace'
import { useSse } from '@/composables/useSse'
import type { Confirmation } from '@/types'

defineEmits<{
  reviewConfirmation: [confirmation: Confirmation]
}>()

const chatStore = useChatStore()
const workspaceStore = useWorkspaceStore()
const sse = useSse()
const messagesContainer = ref<HTMLElement | null>(null)

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 新消息时自动滚动
watch(
  () => chatStore.messages.length,
  () => scrollToBottom(),
)

// 流式输出时滚动到底部
watch(
  () => chatStore.streamingText,
  () => scrollToBottom(),
)

async function handleToolApproval(confirmation: Confirmation, approved: boolean) {
  if (confirmation.kind !== 'TOOL_PERMISSION') return
  await sse.respondApproval(confirmation, approved)
}
</script>

<style scoped>
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: var(--bg-main);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-lg) 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
  text-align: center;
  padding: var(--spacing-xl);
}

.empty-logo {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  background: var(--bg-hover);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--spacing-lg);
  font-size: 1.5rem;
  color: var(--accent);
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
  color: var(--text-muted);
  line-height: 1.5;
}
</style>

