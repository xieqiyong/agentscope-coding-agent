<template>
  <div class="session-list">
    <div class="session-list-header">
      <span class="section-label">Recents</span>
      <button
        class="new-chat-btn"
        @click="clearCurrentSession"
        :disabled="!workspaceStore.currentWorkspace"
        title="开始新对话"
      >
        <i class="pi pi-sliders-h" style="font-size: 0.78rem;"></i>
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
import { useChatStore } from '@/stores/chat'
import { useWorkspaceStore } from '@/stores/workspace'

const chatStore = useChatStore()
const workspaceStore = useWorkspaceStore()

// 会话列表的加载和当前会话恢复由 AgentWorkspaceView 统一负责（避免多处重复请求）。
// 这里只处理用户交互：选中会话、新建、删除。

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
  padding: 0 4px;
}

.session-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-sm) var(--spacing-md);
}

.section-label {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  color: var(--text-secondary);
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
  padding: 16px 10px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  min-height: 40px;
  padding: 7px 8px;
  border-radius: 9px;
  cursor: pointer;
  transition: background 0.15s;
  border-left: none;
}

.session-item:hover {
  background: rgba(237, 232, 221, 0.86);
}

.session-item.active {
  background: rgba(237, 232, 221, 0.96);
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
  font-size: 0.94rem;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item.active .session-title {
  color: var(--text-primary);
}

.session-time {
  display: block;
  font-size: 0.68rem;
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
  border-radius: 8px;
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
