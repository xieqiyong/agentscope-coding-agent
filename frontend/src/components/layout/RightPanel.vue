<template>
  <aside class="right-panel" v-show="uiStore.rightPanelOpen">
    <div class="panel-tabs">
      <div class="tab-bar">
        <button :class="['tab-btn', { active: uiStore.rightPanelTab === 'events' }]" @click="uiStore.setRightPanelTab('events')">
          <i class="pi pi-list" style="font-size: 0.7rem;"></i>
          运行事件
        </button>
        <button :class="['tab-btn', { active: uiStore.rightPanelTab === 'timing' }]" @click="uiStore.setRightPanelTab('timing')">
          <i class="pi pi-clock" style="font-size: 0.7rem;"></i>
          耗时
        </button>
      </div>
      <div class="panel-content">
        <RuntimeEventList v-if="uiStore.rightPanelTab === 'events'" />
        <TimingPanel v-else />
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { useUiStore } from '@/stores/ui'
import RuntimeEventList from '@/components/runtime/RuntimeEventList.vue'
import TimingPanel from '@/components/runtime/TimingPanel.vue'

const uiStore = useUiStore()
</script>

<style scoped>
.right-panel {
  width: var(--right-panel-width);
  position: relative;
  z-index: 2;
  height: 100%;
  background: rgba(255, 254, 250, 0.94);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  backdrop-filter: blur(12px);
  flex-shrink: 0;
}

.panel-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tab-bar {
  display: flex;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.tab-btn {
  flex: 1;
  padding: var(--spacing-sm) var(--spacing-md);
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: var(--font-size-xs);
  font-weight: 500;
  color: var(--text-muted);
  border-bottom: 2px solid transparent;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.tab-btn:hover {
  color: var(--text-secondary);
  background: var(--bg-hover);
}

.tab-btn.active {
  color: var(--ink);
  border-bottom-color: var(--accent);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-sm);
}

@media (max-width: 1100px) {
  .right-panel {
    position: absolute;
    top: 12px;
    right: 12px;
    bottom: 12px;
    height: auto;
    z-index: 8;
  }
}
</style>
