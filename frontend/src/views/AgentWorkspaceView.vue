<template>
  <div class="workspace-layout">
    <LeftSidebar />

    <div class="workspace-main">
      <TopBar />
      <div class="workspace-body">
        <ChatPanel @review-confirmation="openDiffReview" />
        <RightPanel />
      </div>
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
import { useAgentStore } from '@/stores/agent'
import type { Confirmation } from '@/types'

const workspaceStore = useWorkspaceStore()
const chatStore = useChatStore()
const agentStore = useAgentStore()

const showDiffModal = ref(false)
const selectedConfirmation = ref<Confirmation | null>(null)

const activeConfirmation = computed<Confirmation | null>(
  () => selectedConfirmation.value || null,
)

// 启动时恢复上次工作区。selectWorkspace 会触发下面的 watch 统一加载会话，避免 onMounted + watch 双触发。
onMounted(async () => {
  await workspaceStore.fetchWorkspaces()
  const restoredWorkspaceId = workspaceStore.restoreWorkspaceId()
  const workspace = workspaceStore.workspaces.find((item) => String(item.id) === String(restoredWorkspaceId))
    || workspaceStore.workspaces[0]

  if (!workspace) return

  await workspaceStore.selectWorkspace(workspace.id)
})

// 唯一会话加载入口：工作区变化时拉取会话列表并恢复当前会话。
// AgentWorkspaceView 始终 mounted，比放在 SessionList 更稳定（不会因侧栏折叠重复挂载）。
watch(
  () => workspaceStore.currentWorkspace?.id,
  async (newId, oldId) => {
    if (!newId || String(newId) === String(oldId || '')) return
    await agentStore.fetchAgents(newId)
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
  overflow: hidden;
  background: var(--bg-main);
}

.workspace-main {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(circle at 50% 34%, rgba(217, 109, 74, 0.06), transparent 28rem),
    var(--bg-main);
}

.workspace-body {
  flex: 1;
  min-height: 0;
  position: relative;
  display: flex;
  overflow: hidden;
}
</style>
