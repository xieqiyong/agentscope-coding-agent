<template>
  <header class="topbar">
    <div class="topbar-left">
      <button class="topbar-icon-btn" @click="uiStore.toggleLeftSidebar()" title="切换侧栏">
        <i class="pi pi-bars"></i>
      </button>
      <div class="topbar-brand">
        <i class="pi pi-bolt" style="color: var(--accent);"></i>
        <span class="topbar-title">CodingAgent</span>
      </div>
    </div>

    <div class="topbar-center">
      <!-- 当前会话标题或工作区信息 -->
      <div v-if="chatStore.currentSession" class="session-info">
        <i class="pi pi-comment" style="font-size: 0.75rem; color: var(--text-muted);"></i>
        <span class="session-title-bar">{{ chatStore.currentSession.title || '未命名会话' }}</span>
      </div>
      <div v-else-if="workspaceStore.currentWorkspace" class="session-info">
        <i class="pi pi-folder" style="font-size: 0.75rem; color: var(--text-muted);"></i>
        <span class="session-title-bar">{{ workspaceStore.currentWorkspace.name }}</span>
      </div>
    </div>

    <div class="topbar-right">
      <!-- 工作区选择器 -->
      <div class="workspace-selector-wrap">
        <Select
          v-model="selectedWorkspaceId"
          :options="workspaceOptions"
          optionLabel="label"
          optionValue="value"
          placeholder="工作区..."
          class="workspace-select"
          @change="onWorkspaceChange"
        />
        <button class="topbar-icon-btn add-ws" @click="showRegisterDialog = true" title="注册新工作区">
          <i class="pi pi-plus" style="font-size: 0.7rem;"></i>
        </button>
      </div>

      <div class="topbar-divider"></div>

      <!-- 运行面板切换 -->
      <button
        class="topbar-icon-btn"
        @click="uiStore.toggleRightPanel()"
        :class="{ active: uiStore.rightPanelOpen }"
        title="运行面板"
      >
        <i class="pi pi-chart-line"></i>
      </button>
      <!-- 设置 -->
      <router-link to="/settings" class="topbar-icon-btn" title="设置">
        <i class="pi pi-cog"></i>
      </router-link>
    </div>

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

.topbar-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.topbar-brand {
  display: flex;
  align-items: center;
  gap: 6px;
}

.topbar-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.2px;
}

.topbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
  min-width: 0;
}

.session-info {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
}

.session-title-bar {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.topbar-divider {
  width: 1px;
  height: 20px;
  background: var(--border-color);
}

.workspace-selector-wrap {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.workspace-select {
  width: 160px;
  font-size: var(--font-size-xs);
}

.add-ws {
  width: 24px;
  height: 24px;
  font-size: 0.7rem;
  background: var(--accent);
  color: white;
  border-radius: var(--radius-sm);
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
  color: var(--text-muted);
  transition: all 0.15s;
  text-decoration: none;
  font-size: var(--font-size-sm);
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
