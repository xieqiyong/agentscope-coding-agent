<template>
  <aside class="left-sidebar" v-show="uiStore.leftSidebarOpen">
    <div class="sidebar-content">
      <!-- Sessions -->
      <div class="sidebar-section">
        <div class="section-header" @click="sessionsOpen = !sessionsOpen">
          <i class="pi pi-comments" style="font-size: 0.85rem;"></i>
          <span class="section-title">会话</span>
          <i :class="['pi', sessionsOpen ? 'pi-chevron-down' : 'pi-chevron-right']" style="font-size: 0.7rem;"></i>
        </div>
        <div class="section-body" v-show="sessionsOpen">
          <SessionList />
        </div>
      </div>

      <!-- File Tree -->
      <div class="sidebar-section">
        <div class="section-header" @click="filesOpen = !filesOpen">
          <i class="pi pi-folder" style="font-size: 0.85rem;"></i>
          <span class="section-title">文件</span>
          <i :class="['pi', filesOpen ? 'pi-chevron-down' : 'pi-chevron-right']" style="font-size: 0.7rem;"></i>
        </div>
        <div class="section-body" v-show="filesOpen">
          <FileTreePanel />
        </div>
      </div>

      <!-- Memory -->
      <div class="sidebar-section">
        <div class="section-header" @click="memoryOpen = !memoryOpen">
          <i class="pi pi-bookmark" style="font-size: 0.85rem;"></i>
          <span class="section-title">记忆</span>
          <i :class="['pi', memoryOpen ? 'pi-chevron-down' : 'pi-chevron-right']" style="font-size: 0.7rem;"></i>
        </div>
        <div class="section-body" v-show="memoryOpen">
          <MemoryPanel />
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useUiStore } from '@/stores/ui'
import SessionList from '@/components/session/SessionList.vue'
import FileTreePanel from '@/components/workspace/FileTreePanel.vue'
import MemoryPanel from '@/components/memory/MemoryPanel.vue'

const uiStore = useUiStore()

const sessionsOpen = ref(true)
const filesOpen = ref(false)
const memoryOpen = ref(false)
</script>

<style scoped>
.left-sidebar {
  width: var(--sidebar-width);
  background: var(--bg-panel);
  border-right: 1px solid var(--border-color);
  overflow-y: auto;
  flex-shrink: 0;
}

.sidebar-content {
  padding: var(--spacing-sm) 0;
}

.sidebar-section {
  border-bottom: 1px solid var(--border-color);
}

.section-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}

.section-header:hover {
  background: var(--bg-hover);
}

.section-title {
  flex: 1;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.section-body {
  padding: var(--spacing-xs) var(--spacing-sm);
}
</style>
