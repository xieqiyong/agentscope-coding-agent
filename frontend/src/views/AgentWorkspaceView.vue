<template>
  <div class="workspace-layout">
    <TopBar />

    <div class="workspace-body">
      <LeftSidebar />
      <ChatPanel />
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
import { useRoute } from 'vue-router'
import TopBar from '@/components/layout/TopBar.vue'
import LeftSidebar from '@/components/layout/LeftSidebar.vue'
import ChatPanel from '@/components/layout/ChatPanel.vue'
import RightPanel from '@/components/layout/RightPanel.vue'
import DiffReviewModal from '@/components/diff/DiffReviewModal.vue'
import { useWorkspaceStore } from '@/stores/workspace'
import { useChatStore } from '@/stores/chat'
import type { Confirmation } from '@/types'

const route = useRoute()
const workspaceStore = useWorkspaceStore()
const chatStore = useChatStore()

const showDiffModal = ref(false)

const activeConfirmation = computed<Confirmation | null>(
  () => chatStore.pendingConfirmations[0] || null,
)

// Watch for pending confirmations to auto-open modal
watch(
  () => chatStore.pendingConfirmations.length,
  (len) => {
    if (len > 0) {
      showDiffModal.value = true
    }
  },
)

// Load workspace and session from route params
onMounted(async () => {
  const workspaceId = route.params.workspaceId as string
  const sessionId = route.params.sessionId as string

  if (workspaceId) {
    await workspaceStore.selectWorkspace(workspaceId)
  }

  if (sessionId) {
    await chatStore.selectSession(sessionId)
  }
})

// Re-select session when route changes
watch(
  () => route.params.sessionId,
  async (newSessionId) => {
    if (newSessionId && typeof newSessionId === 'string') {
      await chatStore.selectSession(newSessionId)
    }
  },
)

watch(
  () => route.params.workspaceId,
  async (newWorkspaceId) => {
    if (newWorkspaceId && typeof newWorkspaceId === 'string') {
      await workspaceStore.selectWorkspace(newWorkspaceId)
      await chatStore.fetchSessions(newWorkspaceId)
    }
  },
)

function onDiffResolved() {
  showDiffModal.value = false
}
</script>

<style scoped>
.workspace-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.workspace-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
</style>
