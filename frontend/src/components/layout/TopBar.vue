<template>
  <header class="topbar">
    <div class="topbar-left">
      <button class="topbar-icon-btn" @click="uiStore.toggleLeftSidebar()" title="切换侧栏">
        <i class="pi pi-bars"></i>
      </button>
      <button class="topbar-icon-btn" @click="showRegisterDialog = true" title="注册新工作区">
        <i class="pi pi-plus"></i>
      </button>
    </div>

    <div class="topbar-center">
      <div class="workspace-pill">
        <Select
          v-model="selectedWorkspaceId"
          :options="workspaceOptions"
          optionLabel="label"
          optionValue="value"
          placeholder="选择工作区"
          class="workspace-select"
          @change="onWorkspaceChange"
        />
        <span class="pill-divider">·</span>
        <Select
          v-model="selectedAgentId"
          :options="agentOptions"
          optionLabel="label"
          optionValue="value"
          placeholder="选择 Agent"
          class="agent-select"
          @change="onAgentChange"
        />
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
      <router-link to="/agents" class="topbar-icon-btn" title="智能体">
        <i class="pi pi-sitemap"></i>
      </router-link>
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
import { useAgentStore } from '@/stores/agent'
import RegisterWorkspaceDialog from '@/components/workspace/RegisterWorkspaceDialog.vue'

const uiStore = useUiStore()
const workspaceStore = useWorkspaceStore()
const agentStore = useAgentStore()

const selectedWorkspaceId = ref<string | null>(workspaceStore.currentWorkspace?.id || null)
const selectedAgentId = ref<string | null>(agentStore.currentAgent?.id || null)
const showRegisterDialog = ref(false)

const workspaceOptions = computed(() => workspaceStore.workspaceOptions)
const agentOptions = computed(() => agentStore.agentOptions)

watch(() => workspaceStore.currentWorkspace, (ws) => {
  if (ws) selectedWorkspaceId.value = ws.id
})

watch(() => agentStore.currentAgent, (agent) => {
  selectedAgentId.value = agent?.id || null
})

async function onWorkspaceChange() {
  if (selectedWorkspaceId.value) {
    // 只切换工作区；会话列表由 AgentWorkspaceView 的 watch 统一加载，避免重复请求
    await workspaceStore.selectWorkspace(selectedWorkspaceId.value)
  }
}

function onAgentChange() {
  if (selectedAgentId.value) {
    agentStore.selectAgent(selectedAgentId.value)
  }
}
</script>

<style scoped>
.topbar {
  height: var(--topbar-height);
  background: transparent;
  border-bottom: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px 0;
  flex-shrink: 0;
  position: relative;
  z-index: 4;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.topbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
  min-width: 0;
}

.workspace-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  max-width: min(520px, 52vw);
  padding: 0 12px;
  border-radius: 12px;
  background: rgba(239, 235, 226, 0.76);
  color: var(--text-secondary);
  box-shadow: var(--shadow-sm);
  backdrop-filter: blur(8px);
}

.pill-divider {
  color: var(--text-muted);
}

.pill-link {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.workspace-select {
  width: 180px;
  font-size: var(--font-size-xs);
}

.agent-select {
  width: 168px;
  font-size: var(--font-size-xs);
}

.topbar-icon-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ink);
  transition: all 0.15s;
  text-decoration: none;
  font-size: var(--font-size-sm);
}

.topbar-icon-btn:hover {
  background: var(--bg-hover);
  color: var(--ink);
}

.topbar-icon-btn.active {
  background: var(--ink);
  color: var(--bg-main);
}

:deep(.workspace-select) {
  --p-select-background: transparent;
  --p-select-border-color: transparent;
  --p-select-hover-border-color: transparent;
  --p-select-focus-border-color: transparent;
  --p-select-shadow: none;
  --p-select-color: var(--text-secondary);
  --p-select-placeholder-color: var(--text-muted);
}

:deep(.agent-select) {
  --p-select-background: transparent;
  --p-select-border-color: transparent;
  --p-select-hover-border-color: transparent;
  --p-select-focus-border-color: transparent;
  --p-select-shadow: none;
  --p-select-color: var(--text-secondary);
  --p-select-placeholder-color: var(--text-muted);
}

:deep(.workspace-select .p-select-label),
:deep(.agent-select .p-select-label) {
  padding: 0;
  font-size: var(--font-size-sm);
}

:deep(.workspace-select .p-select-dropdown),
:deep(.agent-select .p-select-dropdown) {
  width: 20px;
}

@media (max-width: 760px) {
  .workspace-pill {
    max-width: 56vw;
  }

  .pill-divider,
  .agent-select {
    display: none;
  }

  .workspace-select {
    width: 150px;
  }
}
</style>
