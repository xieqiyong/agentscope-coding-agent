<template>
  <div class="workspace-layout">
    <TopBar />

    <div class="workspace-body">
      <LeftSidebar />
      <ChatPanel @review-confirmation="openDiffReview" />
      <RightPanel />
    </div>

    <!-- Diff review modal -->
    <DiffReviewModal
      :visible="showDiffModal"
      :confirmation="activeConfirmation"
      @update:visible="showDiffModal = $event"
      @approved="onDiffResolved"
      @rejected="onDiffResolved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import TopBar from '@/components/layout/TopBar.vue'
import LeftSidebar from '@/components/layout/LeftSidebar.vue'
import ChatPanel from '@/components/layout/ChatPanel.vue'
import RightPanel from '@/components/layout/RightPanel.vue'
import DiffReviewModal from '@/components/diff/DiffReviewModal.vue'
import { useWorkspaceStore } from '@/stores/workspace'
import { useChatStore } from '@/stores/chat'
import type { Confirmation } from '@/types'

const workspaceStore = useWorkspaceStore()
const chatStore = useChatStore()

const showDiffModal = ref(false)
const selectedConfirmation = ref<Confirmation | null>(null)

const activeConfirmation = computed<Confirmation | null>(
  () => selectedConfirmation.value || null,
)

// 启动时恢复上次工作区和会话，刷新页面后聊天记录不会丢。
onMounted(async () => {
  await workspaceStore.fetchWorkspaces()
  const restoredWorkspaceId = workspaceStore.restoreWorkspaceId()
  const workspace = workspaceStore.workspaces.find((item) => String(item.id) === String(restoredWorkspaceId))
    || workspaceStore.workspaces[0]

  if (!workspace) return

  await workspaceStore.selectWorkspace(workspace.id)
  await chatStore.fetchSessions(workspace.id)
  await restoreConversation()
})

// 工作区切换时重新加载会话。
watch(
  () => workspaceStore.currentWorkspace?.id,
  async (newId, oldId) => {
    if (!newId || String(newId) === String(oldId || '')) return
    await chatStore.fetchSessions(newId)
    await restoreConversation()
  },
)

async function restoreConversation() {
  if (chatStore.sessions.length === 0) {
    chatStore.clearSession()
    return
  }

  const restoredId = chatStore.restoreConversationId()
  const session = chatStore.sessions.find((item) => String(item.id) === String(restoredId))
    || chatStore.sessions[0]
  if (session) {
    await chatStore.selectSession(String(session.id))
  }
}

function openDiffReview(confirmation: Confirmation) {
  selectedConfirmation.value = confirmation
  showDiffModal.value = true
}

function onDiffResolved() {
  showDiffModal.value = false
  selectedConfirmation.value = null
}
</script>

<style scoped>
.workspace-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-main);
}

.workspace-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
</style>
