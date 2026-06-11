<template>
  <div :class="['tool-call-card', toolCall.status]">
    <div class="tool-header" @click="expanded = !expanded">
      <i :class="toolIcon" style="font-size: 0.75rem;"></i>
      <span class="tool-name">{{ toolCall.toolName }}</span>
      <Tag
        :value="statusLabel"
        :severity="statusSeverity"
        style="font-size: 0.65rem; padding: 0 6px;"
      />
      <span v-if="toolCall.durationMs" class="tool-duration">{{ toolCall.durationMs }}ms</span>
      <i :class="['pi', expanded ? 'pi-chevron-down' : 'pi-chevron-right']" style="font-size: 0.65rem; margin-left: auto;"></i>
    </div>
    <div v-if="expanded" class="tool-body">
      <div class="tool-section">
        <span class="tool-section-label">参数</span>
        <pre class="tool-json">{{ formatJson(toolCall.args) }}</pre>
      </div>
      <div v-if="toolCall.result" class="tool-section">
        <span class="tool-section-label">结果</span>
        <pre class="tool-result">{{ truncate(toolCall.result, 500) }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import Tag from 'primevue/tag'
import type { ToolCallInfo } from '@/types'

const props = defineProps<{
  toolCall: ToolCallInfo
}>()

const expanded = ref(false)

// Auto-expand while running
const statusSeverity = computed(() => {
  switch (props.toolCall.status) {
    case 'running': return 'info'
    case 'completed': return 'success'
    case 'error': return 'danger'
    default: return 'secondary'
  }
})

const statusLabel = computed(() => {
  const labels: Record<string, string> = {
    running: '运行中',
    completed: '已完成',
    error: '出错',
  }
  return labels[props.toolCall.status] || props.toolCall.status
})

const toolIcon = computed(() => {
  const icons: Record<string, string> = {
    list_files: 'pi pi-folder-open',
    read_file: 'pi pi-file',
    search_code: 'pi pi-search',
    propose_patch: 'pi pi-pencil',
  }
  return icons[props.toolCall.toolName] || 'pi pi-wrench'
})

function formatJson(obj: Record<string, unknown>): string {
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

function truncate(text: string, max: number): string {
  return text.length > max ? text.slice(0, max) + '...' : text
}
</script>

<style scoped>
.tool-call-card {
  border: 1px solid var(--border-tool);
  border-radius: var(--radius-sm);
  background: var(--bg-tool-card);
  overflow: hidden;
}

.tool-call-card.running {
  border-color: var(--accent);
}

.tool-call-card.error {
  border-color: var(--danger);
}

.tool-call-card.completed {
  border-color: var(--success);
}

.tool-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  cursor: pointer;
  user-select: none;
}

.tool-header:hover {
  background: var(--bg-hover);
}

.tool-name {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  font-weight: 500;
  color: var(--text-primary);
}

.tool-duration {
  font-family: var(--font-mono);
  font-size: 0.65rem;
  color: var(--text-muted);
}

.tool-body {
  padding: var(--spacing-sm) var(--spacing-md);
  border-top: 1px solid var(--border-color);
}

.tool-section {
  margin-bottom: var(--spacing-sm);
}

.tool-section:last-child {
  margin-bottom: 0;
}

.tool-section-label {
  display: block;
  font-size: 0.65rem;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  margin-bottom: 2px;
}

.tool-json,
.tool-result {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  background: var(--bg-main);
  border-radius: var(--radius-sm);
  padding: var(--spacing-sm);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
}
</style>
