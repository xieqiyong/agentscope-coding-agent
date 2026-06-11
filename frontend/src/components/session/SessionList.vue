<template>
  <div class="session-list">
    <div class="session-list-header">
      <span class="section-label">会话</span>
      <button
        class="new-chat-btn"
        @click="clearCurrentSession"
        :disabled="!workspaceStore.currentWorkspace"
        title="开始新对话"
      >
        <i class="pi pi-plus" style="font-size: 0.7rem;"></i>
        <span>新会话</span>
      </button>
    </div>

    <div v-if="chatStore.sessions.length === 0 && !chatStore.lastConversationId" class="empty-sessions">
      发送消息自动创建会话
    </div>

    <div
      v-for="session in chatStore.sessions"
      :key="session.id"
      :class="['session-item', { active: String(session.id) === String(chatStore.lastConversationId) }]"
      @click="selectSession(session.id)"
    >
      <div class="session-info">
        <span class="session-title">{{ session.title || '未命名' }}</span>
        <span class="session-time">{{ formatTime(session.createdAt) }}</span>
      </div>
      <button
        class="session-delete-btn"
        @click.stop="handleDelete(session.id, session.title)"
        title="删除会话"
      >
        <i class="pi pi-trash" style="font-size: 0.65rem;"></i>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useWorkspaceStore } from '@/stores/workspace'

const chatStore = useChatStore()
const workspaceStore = useWorkspaceStore()

onMounted(async () => {
  if (workspaceStore.currentWorkspace) {
    await chatStore.fetchSessions(workspaceStore.currentWorkspace.id)
  }
})

// 工作区切换时重新加载会话列表
watch(() => workspaceStore.currentWorkspace?.id, async (newId) => {
  if (newId) {
    await chatStore.fetchSessions(newId)
  }
})

function clearCurrentSession() {
  chatStore.clearSession()
}

// 删除会话（带确认）
function handleDelete(sessionId: string, title?: string) {
  const name = title || '未命名会话'
  if (confirm(`确定删除会话「${name}」吗？删除后不可恢复。`)) {
    chatStore.deleteSession(String(sessionId))
  }
}

async function selectSession(sessionId: string) {
  await chatStore.selectSession(String(sessionId))
}

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  const diffH = Math.floor(diffMin / 60)
  if (diffH < 24) return `${diffH}小时前`
  return d.toLocaleDateString()
}
</script>

<style scoped>
.session-list {
  display: flex;
  flex-direction: column;
}

.session-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-sm) var(--spacing-md);
}

.section-label {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 1px solid var(--border-color);
  background: transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  transition: all 0.15s;
}

.new-chat-btn:hover:not(:disabled) {
  background: var(--bg-hover);
  color: var(--text-primary);
  border-color: var(--accent);
}

.new-chat-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.empty-sessions {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  text-align: center;
  padding: var(--spacing-lg) var(--spacing-md);
}

.session-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: 0;
  cursor: pointer;
  transition: background 0.15s;
  border-left: 2px solid transparent;
}

.session-item:hover {
  background: var(--bg-hover);
}

.session-item.active {
  background: var(--bg-hover);
  border-left-color: var(--accent);
}

.session-item.active .session-title {
  color: var(--text-primary);
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item.active .session-title {
  color: var(--text-primary);
}

.session-time {
  display: block;
  font-size: 0.65rem;
  color: var(--text-muted);
  margin-top: 2px;
}

/* 删除按钮：默认隐藏，hover 时显示 */
.session-delete-btn {
  opacity: 0;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  transition: all 0.15s;
}

.session-item:hover .session-delete-btn {
  opacity: 1;
}

.session-delete-btn:hover {
  color: var(--danger);
  background: rgba(239, 68, 68, 0.1);
}
</style>

