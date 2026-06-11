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

// Load workspaces on mount, auto-select first if available
onMounted(async () => {
  await workspaceStore.fetchWorkspaces()
  if (!workspaceStore.currentWorkspace && workspaceStore.workspaces.length > 0) {
    await workspaceStore.selectWorkspace(workspaceStore.workspaces[0].id)
    await chatStore.fetchSessions(workspaceStore.workspaces[0].id)
  }
})

// Watch workspace change → reload sessions
watch(
  () => workspaceStore.currentWorkspace?.id,
  async (newId) => {
    if (newId) {
      await chatStore.fetchSessions(newId)
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
