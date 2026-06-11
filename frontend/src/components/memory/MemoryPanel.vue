<template>
  <div class="memory-panel">
    <!-- Filter tabs -->
    <div class="memory-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        :class="['memory-tab', { active: memoryStore.filter === tab.value }]"
        @click="memoryStore.setFilter(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- Memory list -->
    <div v-if="memoryStore.filteredMemories.length === 0" class="empty-memory">
      暂无记忆。
    </div>
    <div v-else class="memory-list">
      <MemoryItem
        v-for="mem in memoryStore.filteredMemories"
        :key="mem.id"
        :memory="mem"
        @approve="memoryStore.approveMemory(mem.id)"
        @reject="memoryStore.rejectMemory(mem.id)"
        @disable="memoryStore.disableMemory(mem.id)"
      />
    </div>

    <!-- Add button -->
    <div class="memory-actions">
      <Button
        label="添加记忆"
        icon="pi pi-plus"
        size="small"
        text
        @click="showCreateDialog = true"
      />
    </div>

    <CreateMemoryDialog v-model:visible="showCreateDialog" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Button from 'primevue/button'
import { useMemoryStore } from '@/stores/memory'
import { useWorkspaceStore } from '@/stores/workspace'
import MemoryItem from './MemoryItem.vue'
import CreateMemoryDialog from './CreateMemoryDialog.vue'
import type { MemoryStatus } from '@/types'

const memoryStore = useMemoryStore()
const workspaceStore = useWorkspaceStore()
const showCreateDialog = ref(false)

const tabs: { label: string; value: MemoryStatus | 'ALL' }[] = [
  { label: '全部', value: 'ALL' },
  { label: '已生效', value: 'ACTIVE' },
  { label: '待审核', value: 'CANDIDATE' },
  { label: '冲突', value: 'CONFLICT' },
]

onMounted(() => {
  if (workspaceStore.currentWorkspace) {
    memoryStore.fetchMemories(workspaceStore.currentWorkspace.id)
  }
})
</script>

<style scoped>
.memory-panel {
  display: flex;
  flex-direction: column;
}

.memory-tabs {
  display: flex;
  gap: 2px;
  padding: var(--spacing-xs) var(--spacing-sm);
  overflow-x: auto;
}

.memory-tab {
  padding: 2px 8px;
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  cursor: pointer;
  color: var(--text-muted);
  white-space: nowrap;
}

.memory-tab:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.memory-tab.active {
  background: var(--accent);
  color: white;
}

.empty-memory {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  text-align: center;
  padding: var(--spacing-md);
}

.memory-list {
  max-height: 200px;
  overflow-y: auto;
  padding: 0 var(--spacing-sm);
}

.memory-actions {
  padding: var(--spacing-xs) var(--spacing-sm);
  display: flex;
  justify-content: center;
}
</style>
