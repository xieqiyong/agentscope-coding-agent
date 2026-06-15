<template>
  <div class="confirmation-card">
    <div class="confirm-header">
      <i class="pi pi-exclamation-circle" style="color: var(--warning);"></i>
      <span class="confirm-title">{{ title }}</span>
      <Tag :value="confirmation.riskLevel" :severity="riskSeverity" />
    </div>
    <p class="confirm-summary">{{ confirmation.summary }}</p>
    <div v-if="confirmation.kind === 'TOOL_PERMISSION'" class="confirm-tool">
      <span class="tool-name">{{ confirmation.toolName }}</span>
      <span v-if="confirmation.toolCallId" class="tool-id">{{ confirmation.toolCallId }}</span>
    </div>
    <div v-else class="confirm-files">
      <span v-for="file in confirmation.files" :key="file.path" class="file-badge">
        <i :class="fileChangeIcon(file.changeType)" style="font-size: 0.7rem;"></i>
        {{ file.path }}
        <span class="file-stats">+{{ file.additions }}/-{{ file.deletions }}</span>
      </span>
    </div>
    <div class="confirm-actions">
      <template v-if="confirmation.kind === 'TOOL_PERMISSION'">
        <Button label="拒绝" icon="pi pi-times" size="small" severity="danger" outlined @click="$emit('reject', confirmation)" />
        <Button label="批准执行" icon="pi pi-check" size="small" severity="success" @click="$emit('approve', confirmation)" />
      </template>
      <Button v-else label="查看 Diff" icon="pi pi-eye" size="small" @click="$emit('review', confirmation)" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import type { Confirmation, RiskLevel } from '@/types'

const props = defineProps<{
  confirmation: Confirmation
}>()

defineEmits<{
  review: [confirmation: Confirmation]
  approve: [confirmation: Confirmation]
  reject: [confirmation: Confirmation]
}>()

const title = computed(() => props.confirmation.kind === 'TOOL_PERMISSION' ? '工具执行确认' : '提议的修改')

const riskSeverity = computed(() => {
  const map: Record<RiskLevel, string> = {
    LOW: 'success',
    MEDIUM: 'warn',
    HIGH: 'danger',
    CRITICAL: 'danger',
  }
  return map[props.confirmation.riskLevel] || 'info'
})

function fileChangeIcon(type: string): string {
  const icons: Record<string, string> = {
    added: 'pi pi-plus',
    modified: 'pi pi-pencil',
    deleted: 'pi pi-minus',
  }
  return icons[type] || 'pi pi-file'
}
</script>

<style scoped>
.confirmation-card {
  border: 2px solid var(--border-confirm);
  border-radius: var(--radius-md);
  background: #fffbeb;
  padding: var(--spacing-md);
}

.confirm-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.confirm-title {
  font-weight: 600;
  font-size: var(--font-size-base);
  color: var(--text-primary);
}

.confirm-summary {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-bottom: var(--spacing-sm);
}

.confirm-files {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
  margin-bottom: var(--spacing-md);
}

.confirm-tool {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.tool-name,
.tool-id {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-panel);
  padding: 3px 8px;
}

.tool-id {
  color: var(--text-muted);
}

.file-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 2px 8px;
}

.file-stats {
  color: var(--text-muted);
  font-size: 0.65rem;
}

.confirm-actions {
  display: flex;
  gap: var(--spacing-sm);
}
</style>
