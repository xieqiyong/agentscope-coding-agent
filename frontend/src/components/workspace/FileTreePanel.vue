<template>
  <div class="file-tree-panel">
    <div v-if="!workspaceStore.currentWorkspace" class="placeholder">
      请先选择一个工作区
    </div>
    <div v-else-if="!loaded" class="placeholder">
      <Button label="加载文件" size="small" text @click="loadTree" />
    </div>
    <div v-else-if="treeNodes.length === 0" class="placeholder">
      工作区为空
    </div>
    <div v-else class="tree-container">
      <div
        v-for="node in treeNodes"
        :key="node.key"
      >
        <TreeNodeItem :node="node" :depth="0" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import Button from 'primevue/button'
import { useWorkspaceStore } from '@/stores/workspace'
import type { FileNode } from '@/types'
import TreeNodeItem from './TreeNodeItem.vue'

const workspaceStore = useWorkspaceStore()
const loaded = ref(false)

const treeNodes = computed(() => workspaceStore.fileTree)

async function loadTree() {
  await workspaceStore.fetchFileTree()
  loaded.value = true
}
</script>

<style scoped>
.file-tree-panel {
  max-height: 350px;
  overflow-y: auto;
}

.placeholder {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  text-align: center;
  padding: var(--spacing-md);
}

.tree-container {
  font-size: var(--font-size-xs);
}
</style>
