<template>
  <div class="session-list">
    <div class="session-list-header">
      <Button
        label="新建"
        icon="pi pi-plus"
        size="small"
        text
        @click="createNewSession"
        :disabled="!workspaceStore.currentWorkspace"
      />
    </div>
    <div v-if="chatStore.sessions.length === 0" class="empty-sessions">
      暂无会话
    </div>
    <div
      v-for="session in chatStore.sessions"
      :key="session.id"
      :class="['session-item', { active: chatStore.currentSession?.id === session.id }]"
      @click="selectSession(session.id)"
    >
      <i class="pi pi-comment" style="font-size: 0.75rem; color: var(--text-muted);"></i>
      <div class="session-info">
        <span class="session-title">{{ session.title || '未命名' }}</span>
        <span class="session-time">{{ formatTime(session.createdAt) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Button from 'primevue/button'
import { useChatStore } from '@/stores/chat'
import { useWorkspaceStore } from '@/stores/workspace'

const chatStore = useChatStore()
const workspaceStore = useWorkspaceStore()
const router = useRouter()
const route = useRoute()

onMounted(async () => {
  if (workspaceStore.currentWorkspace) {
    await chatStore.fetchSessions(workspaceStore.currentWorkspace.id)
  }
})

async function createNewSession() {
  if (!workspaceStore.currentWorkspace) return
  const session = await chatStore.createSession(workspaceStore.currentWorkspace.id)
  if (session) {
    router.push({
      name: 'workspace-session',
      params: {
        workspaceId: workspaceStore.currentWorkspace.id,
        sessionId: session.id,
      },
    })
  }
}

function selectSession(sessionId: string) {
  if (!workspaceStore.currentWorkspace) return
  router.push({
    name: 'workspace-session',
    params: {
      workspaceId: workspaceStore.currentWorkspace.id,
      sessionId,
    },
  })
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
  padding: var(--spacing-xs) var(--spacing-sm);
  display: flex;
  justify-content: flex-end;
}

.empty-sessions {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  text-align: center;
  padding: var(--spacing-md);
}

.session-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s;
}

.session-item:hover {
  background: var(--bg-hover);
}

.session-item.active {
  background: var(--accent);
  color: white;
}

.session-item.active .session-title {
  color: white;
}

.session-item.active .session-time {
  color: rgba(255, 255, 255, 0.7);
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  margin-top: 2px;
}
</style>
