<template>
  <div class="timing-panel">
    <div v-if="!runtimeStore.agentStartTime" class="empty-timing">
      运行一次 Agent 任务后查看耗时统计
    </div>
    <template v-else>
      <div class="stat-row">
        <span class="stat-label">Agent 总耗时</span>
        <span class="stat-value">{{ formatMs(runtimeStore.totalAgentTimeMs) }}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">模型调用</span>
        <span class="stat-value">{{ runtimeStore.modelCallCount }} 次 · {{ formatMs(runtimeStore.totalModelTimeMs) }}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">工具调用</span>
        <span class="stat-value">{{ runtimeStore.toolCallCount }} 次 · {{ formatMs(runtimeStore.totalToolTimeMs) }}</span>
      </div>

      <div v-if="perToolEntries.length > 0" class="per-tool-section">
        <div class="section-label">按工具统计</div>
        <div v-for="[name, stats] in perToolEntries" :key="name" class="stat-row">
          <span class="stat-label tool-name">{{ name }}</span>
          <span class="stat-value">{{ stats.count }}x · {{ formatMs(stats.totalTimeMs) }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRuntimeStore } from '@/stores/runtime'

const runtimeStore = useRuntimeStore()

const perToolEntries = computed(() => Array.from(runtimeStore.perToolStats.entries()))

function formatMs(ms: number): string {
  if (!ms) return '0ms'
  if (ms < 1000) return `${Math.round(ms)}ms`
  return `${(ms / 1000).toFixed(1)}s`
}
</script>

<style scoped>
.timing-panel {
  padding: var(--spacing-sm);
}

.empty-timing {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  text-align: center;
  padding: var(--spacing-lg);
}

.stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-xs) 0;
  font-size: var(--font-size-sm);
}

.stat-label {
  color: var(--text-secondary);
}

.stat-value {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: var(--text-primary);
  font-weight: 500;
}

.section-label {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-top: var(--spacing-md);
  margin-bottom: var(--spacing-xs);
  padding-bottom: var(--spacing-xs);
  border-bottom: 1px solid var(--border-color);
}

.tool-name {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}
</style>
