<template>
  <header class="topbar">
    <div class="topbar-left">
      <button class="topbar-icon-btn" @click="uiStore.toggleLeftSidebar()" title="切换侧栏">
        <i class="pi pi-bars"></i>
      </button>
      <span class="topbar-title">
        <i class="pi pi-bolt" style="color: var(--accent); margin-right: 4px;"></i>
        编码助手
      </span>
    </div>

    <div class="topbar-center">
      <div class="workspace-selector-wrap">
        <Select
          v-model="selectedWorkspaceId"
          :options="workspaceOptions"
          optionLabel="label"
          optionValue="value"
          placeholder="选择工作区..."
          class="workspace-select"
          @change="onWorkspaceChange"
        />
        <button class="topbar-icon-btn add-ws" @click="showRegisterDialog = true" title="注册新工作区">
          <i class="pi pi-plus"></i>
        </button>
      </div>
    </div>

    <div class="topbar-right">
      <button
        class="topbar-icon-btn"
        @click="uiStore.toggleRightPanel()"
        :class="{ active: uiStore.rightPanelOpen }"
        title="运行面板"
      >
        <i class="pi pi-chart-line"></i>
      </button>
      <router-link to="/settings" class="topbar-icon-btn" title="设置">
        <i class="pi pi-cog"></i>
      </router-link>
    </div>

    <!-- Register workspace dialog -->
    <RegisterWorkspaceDialog v-model:visible="showRegisterDialog" />
  </header>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import Select from 'primevue/select'
import { useUiStore } from '@/stores/ui'
import { useWorkspaceStore } from '@/stores/workspace'
import { useChatStore } from '@/stores/chat'
import RegisterWorkspaceDialog from '@/components/workspace/RegisterWorkspaceDialog.vue'

const uiStore = useUiStore()
const workspaceStore = useWorkspaceStore()
const chatStore = useChatStore()

const selectedWorkspaceId = ref<string | null>(workspaceStore.currentWorkspace?.id || null)
const showRegisterDialog = ref(false)

const workspaceOptions = computed(() => workspaceStore.workspaceOptions)

watch(() => workspaceStore.currentWorkspace, (ws) => {
  if (ws) selectedWorkspaceId.value = ws.id
})

async function onWorkspaceChange() {
  if (selectedWorkspaceId.value) {
    await workspaceStore.selectWorkspace(selectedWorkspaceId.value)
    await chatStore.fetchSessions(selectedWorkspaceId.value)
    chatStore.clearSession()
  }
}
</script>

<style scoped>
.topbar {
  height: var(--topbar-height);
  background: var(--bg-topbar);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-md);
  flex-shrink: 0;
}

.topbar-left,
.topbar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.topbar-title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
}

.topbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.workspace-selector-wrap {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.workspace-select {
  width: 240px;
  font-size: var(--font-size-sm);
}

.add-ws {
  width: 28px;
  height: 28px;
  font-size: 0.8rem;
  background: var(--accent);
  color: white;
  border-radius: 50%;
}

.add-ws:hover {
  background: var(--accent-hover);
}

.topbar-icon-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  transition: all 0.15s;
  text-decoration: none;
}

.topbar-icon-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.topbar-icon-btn.active {
  background: var(--accent);
  color: white;
}
</style>
