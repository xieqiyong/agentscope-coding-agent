<template>
  <div :class="['tool-call-card', toolCall.status]">
    <div class="tool-header" @click="expanded = !expanded">
      <i :class="toolIcon" style="font-size: 0.7rem;"></i>
      <span class="tool-name">{{ toolSignature }}</span>
      <span :class="['tool-status', toolCall.status]">{{ statusLabel }}</span>
      <span v-if="toolCall.durationMs" class="tool-duration">{{ toolCall.durationMs }}ms</span>
      <i :class="['pi', expanded ? 'pi-chevron-down' : 'pi-chevron-right']" style="font-size: 0.6rem; margin-left: auto;"></i>
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
import type { ToolCallInfo } from '@/types'

const props = defineProps<{
  toolCall: ToolCallInfo
}>()

const expanded = ref(false)

const toolSignature = computed(() => {
  const entries = Object.entries(props.toolCall.args || {})
    .filter(([key]) => key !== '_raw')
    .map(([key, value]) => `${key}=${formatArgValue(value)}`)
  if (entries.length > 0) {
    return `${props.toolCall.toolName}(${entries.join(', ')})`
  }
  if (props.toolCall.argsText?.trim()) {
    return `${props.toolCall.toolName}(${props.toolCall.argsText.trim()})`
  }
  return `${props.toolCall.toolName}(...)`
})
const statusLabel = computed(() => {
  const labels: Record<string, string> = {
    running: '运行中',
    completed: '完成',
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
    propose_file_change: 'pi pi-file-edit',
  }
  return icons[props.toolCall.toolName] || 'pi pi-wrench'
})


function formatArgValue(value: unknown): string {
  if (typeof value === 'string') {
    return JSON.stringify(value)
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  if (value == null) {
    return 'null'
  }
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}
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
  border: 1px solid var(--border-color);
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
  border-color: rgba(34, 197, 94, 0.3);
}

.tool-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  min-width: 0;
  padding: 6px var(--spacing-md);
  cursor: pointer;
  user-select: none;
}

.tool-header:hover {
  background: var(--bg-hover);
}

.tool-name {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
  color: var(--text-primary);
}

.tool-status {
  font-size: 0.6rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  flex-shrink: 0;
}

.tool-status.running { color: var(--accent); }
.tool-status.completed { color: var(--success); }
.tool-status.error { color: var(--danger); }

.tool-duration {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  color: var(--text-muted);
  flex-shrink: 0;
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
  font-size: 0.6rem;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  margin-bottom: 2px;
}

.tool-json,
.tool-result {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  background: #0d1117;
  color: #c9d1d9;
  border-radius: var(--radius-sm);
  padding: var(--spacing-sm);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
}
</style>



