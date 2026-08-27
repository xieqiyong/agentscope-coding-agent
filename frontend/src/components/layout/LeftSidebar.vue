<template>
  <aside :class="['left-sidebar', { collapsed: !uiStore.leftSidebarOpen }]">
    <!-- 折叠时只显示图标按钮 -->
    <div v-if="!uiStore.leftSidebarOpen" class="sidebar-collapsed-icons">
      <button class="sidebar-icon-item" @click="uiStore.toggleLeftSidebar()" title="展开侧栏">
        <i class="pi pi-bars"></i>
      </button>
      <button class="sidebar-icon-item" @click="startNewChat" title="新对话">
        <i class="pi pi-plus"></i>
      </button>
      <router-link class="sidebar-icon-item" to="/agents" title="智能体">
        <i class="pi pi-sitemap"></i>
      </router-link>
      <router-link class="sidebar-icon-item" to="/skills" title="Skills">
        <i class="pi pi-bolt"></i>
      </router-link>
      <router-link class="sidebar-icon-item" to="/mcp" title="MCP 服务">
        <i class="pi pi-server"></i>
      </router-link>
    </div>

    <!-- 展开时显示完整内容 -->
    <div v-else class="sidebar-content">
      <div class="sidebar-top">
        <div class="brand-row">
          <div class="brand-name">Coding Agent</div>
          <div class="brand-actions">
            <button class="brand-icon-btn" @click="uiStore.toggleLeftSidebar()" title="折叠侧栏">
              <i class="pi pi-window-minimize"></i>
            </button>
          </div>
        </div>

        <nav class="main-nav" aria-label="主导航">
          <button class="nav-item strong" type="button" @click="startNewChat">
            <span class="nav-icon round"><i class="pi pi-plus"></i></span>
            <span>新对话</span>
          </button>
          <button class="nav-item" type="button" @click="filesOpen = !filesOpen">
            <span class="nav-icon"><i class="pi pi-folder"></i></span>
            <span>文件</span>
          </button>
          <router-link class="nav-item nav-link" to="/agents">
            <span class="nav-icon"><i class="pi pi-sitemap"></i></span>
            <span>智能体</span>
          </router-link>
          <router-link class="nav-item nav-link" to="/skills">
            <span class="nav-icon"><i class="pi pi-bolt"></i></span>
            <span>Skills</span>
          </router-link>
          <router-link class="nav-item nav-link" to="/mcp">
            <span class="nav-icon"><i class="pi pi-server"></i></span>
            <span>MCP 服务</span>
          </router-link>
          <button class="nav-item" type="button" @click="memoryOpen = !memoryOpen">
            <span class="nav-icon"><i class="pi pi-briefcase"></i></span>
            <span>记忆</span>
          </button>
        </nav>
      </div>

      <div class="sidebar-scroll">
        <div class="sidebar-section recents-section">
          <SessionList />
        </div>

        <div class="sidebar-section soft-section" v-show="filesOpen">
          <div class="section-header">
            <i class="pi pi-folder" style="font-size: 0.75rem;"></i>
            <span class="section-title">文件</span>
          </div>
          <div class="section-body">
            <FileTreePanel />
          </div>
        </div>

        <div class="sidebar-section soft-section" v-show="memoryOpen">
          <div class="section-header">
            <i class="pi pi-bookmark" style="font-size: 0.75rem;"></i>
            <span class="section-title">记忆</span>
          </div>
          <div class="section-body">
            <MemoryPanel />
          </div>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useUiStore } from '@/stores/ui'
import { useChatStore } from '@/stores/chat'
import SessionList from '@/components/session/SessionList.vue'
import FileTreePanel from '@/components/workspace/FileTreePanel.vue'
import MemoryPanel from '@/components/memory/MemoryPanel.vue'

const uiStore = useUiStore()
const chatStore = useChatStore()

const filesOpen = ref(false)
const memoryOpen = ref(false)

function startNewChat() {
  chatStore.clearSession()
}
</script>

<style scoped>
.left-sidebar {
  width: var(--sidebar-width);
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-color);
  overflow: hidden;
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
  padding-top: 12px;
  gap: var(--spacing-xs);
}

.sidebar-icon-item {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  transition: all 0.15s;
  font-size: var(--font-size-sm);
  text-decoration: none;
}

.sidebar-icon-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.sidebar-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 12px 0 0;
}

.sidebar-top {
  padding: 0 10px 12px;
  flex-shrink: 0;
}

.brand-row {
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2px 16px;
  margin-bottom: 14px;
}

.brand-name {
  font-family: var(--font-serif);
  font-size: 1.82rem;
  line-height: 1;
  color: var(--ink);
  letter-spacing: -0.04em;
}

.brand-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.brand-icon-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--ink);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.brand-icon-btn:hover {
  background: var(--bg-hover);
}

.main-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  height: 40px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--ink);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 2px;
  font-size: 1rem;
  text-align: left;
  cursor: pointer;
  text-decoration: none;
}

.nav-link {
  width: 100%;
}

.nav-item:hover:not(:disabled) {
  background: rgba(237, 232, 221, 0.8);
}

.nav-item.strong {
  font-weight: 500;
}

.nav-icon {
  width: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: inherit;
}

.nav-icon.round {
  width: 28px;
  height: 28px;
  margin-left: 2px;
  border-radius: 50%;
  background: var(--bg-hover);
}

.sidebar-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 0 8px;
}

.sidebar-section {
  border-bottom: none;
}

.recents-section {
  margin-top: 4px;
}

.soft-section {
  margin: 8px 10px;
  border-radius: 12px;
  background: rgba(255, 254, 250, 0.42);
  border: 1px solid rgba(223, 216, 204, 0.7);
}

.section-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  cursor: default;
  user-select: none;
  transition: background 0.15s;
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

@media (max-width: 900px) {
  .left-sidebar {
    width: 292px;
  }
}
</style>
