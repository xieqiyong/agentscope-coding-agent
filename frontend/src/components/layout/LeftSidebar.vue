<template>
  <aside :class="['left-sidebar', { collapsed: !uiStore.leftSidebarOpen }]">
    <!-- 折叠时只显示图标按钮 -->
    <div v-if="!uiStore.leftSidebarOpen" class="sidebar-collapsed-icons">
      <button class="sidebar-icon-item" @click="uiStore.toggleLeftSidebar()" title="展开侧栏">
        <i class="pi pi-comments"></i>
      </button>
      <button class="sidebar-icon-item" @click="uiStore.toggleLeftSidebar()" title="文件">
        <i class="pi pi-folder"></i>
      </button>
      <button class="sidebar-icon-item" @click="uiStore.toggleLeftSidebar()" title="记忆">
        <i class="pi pi-bookmark"></i>
      </button>
    </div>

    <!-- 展开时显示完整内容 -->
    <div v-else class="sidebar-content">
      <!-- 会话列表 -->
      <div class="sidebar-section">
        <SessionList />
      </div>

      <!-- 文件树（可折叠） -->
      <div class="sidebar-section">
        <div class="section-header" @click="filesOpen = !filesOpen">
          <i class="pi pi-folder" style="font-size: 0.75rem;"></i>
          <span class="section-title">文件</span>
          <i :class="['pi', filesOpen ? 'pi-chevron-down' : 'pi-chevron-right']" style="font-size: 0.6rem;"></i>
        </div>
        <div class="section-body" v-show="filesOpen">
          <FileTreePanel />
        </div>
      </div>

      <!-- 记忆（可折叠） -->
      <div class="sidebar-section">
        <div class="section-header" @click="memoryOpen = !memoryOpen">
          <i class="pi pi-bookmark" style="font-size: 0.75rem;"></i>
          <span class="section-title">记忆</span>
          <i :class="['pi', memoryOpen ? 'pi-chevron-down' : 'pi-chevron-right']" style="font-size: 0.6rem;"></i>
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

const filesOpen = ref(false)
const memoryOpen = ref(false)
</script>

<style scoped>
.left-sidebar {
  width: var(--sidebar-width);
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-color);
  overflow-y: auto;
  flex-shrink: 0;
  transition: width 0.2s ease;
}

.left-sidebar.collapsed {
  width: 48px;
}

.sidebar-collapsed-icons {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: var(--spacing-sm);
  gap: var(--spacing-xs);
}

.sidebar-icon-item {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  transition: all 0.15s;
  font-size: var(--font-size-sm);
}

.sidebar-icon-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.sidebar-content {
  padding: 0;
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
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.section-body {
  padding: var(--spacing-xs) var(--spacing-sm);
}
</style>
