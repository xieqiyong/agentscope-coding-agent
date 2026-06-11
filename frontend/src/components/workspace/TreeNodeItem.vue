<template>
  <div class="tree-node">
    <div
      class="tree-row"
      :style="{ paddingLeft: depth * 12 + 'px' }"
      @click="toggle"
    >
      <i
        v-if="node.isDirectory"
        :class="['pi', expanded ? 'pi-folder-open' : 'pi-folder']"
        style="font-size: 0.75rem; color: var(--accent);"
      />
      <i
        v-else
        class="pi pi-file"
        style="font-size: 0.75rem; color: var(--text-muted);"
      />
      <span class="tree-label">{{ node.label }}</span>
    </div>
    <div v-if="expanded && node.children?.length">
      <TreeNodeItem
        v-for="child in node.children"
        :key="child.key"
        :node="child"
        :depth="depth + 1"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { FileNode } from '@/types'

const props = defineProps<{
  node: FileNode
  depth: number
}>()

const expanded = ref(false)

function toggle() {
  if (props.node.isDirectory) {
    expanded.value = !expanded.value
  }
}
</script>

<style scoped>
.tree-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 1px 4px;
  cursor: pointer;
  border-radius: 2px;
  transition: background 0.1s;
}

.tree-row:hover {
  background: var(--bg-hover);
}

.tree-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-secondary);
}
</style>
