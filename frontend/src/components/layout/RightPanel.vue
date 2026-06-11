<template>
  <aside class="right-panel" v-show="uiStore.rightPanelOpen">
    <div class="panel-tabs">
      <div class="tab-bar">
        <button :class="['tab-btn', { active: uiStore.rightPanelTab === 'events' }]" @click="uiStore.setRightPanelTab('events')">
          运行事件
        </button>
        <button :class="['tab-btn', { active: uiStore.rightPanelTab === 'timing' }]" @click="uiStore.setRightPanelTab('timing')">
          耗时统计
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
  background: var(--bg-panel);
  border-left: 1px solid var(--border-color);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
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
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-muted);
  border-bottom: 2px solid transparent;
  transition: all 0.15s;
}

.tab-btn:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.tab-btn.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-sm);
}
</style>
