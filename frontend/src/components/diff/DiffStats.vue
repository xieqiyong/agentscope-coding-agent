<template>
  <div class="diff-stats">
    <div class="stat">
      <i class="pi pi-file" style="font-size: 0.8rem; color: var(--text-muted);"></i>
      <span>{{ files.length }} 个文件被修改</span>
    </div>
    <div class="stat additions">
      <span>+{{ totalAdditions }}</span>
    </div>
    <div class="stat deletions">
      <span>-{{ totalDeletions }}</span>
    </div>
    <Tag
      v-if="riskLevel"
      :value="riskLevel"
      :severity="riskSeverity"
      style="margin-left: auto;"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Tag from 'primevue/tag'
import type { PatchFile, RiskLevel } from '@/types'

const props = defineProps<{
  files: PatchFile[]
  riskLevel?: RiskLevel
}>()

const totalAdditions = computed(() =>
  props.files.reduce((sum, f) => sum + f.additions, 0),
)

const totalDeletions = computed(() =>
  props.files.reduce((sum, f) => sum + f.deletions, 0),
)

const riskSeverity = computed(() => {
  const map: Record<string, string> = {
    LOW: 'success',
    MEDIUM: 'warn',
    HIGH: 'danger',
    CRITICAL: 'danger',
  }
  return map[props.riskLevel || ''] || 'info'
})
</script>

<style scoped>
.diff-stats {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) 0;
  margin-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
}

.stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.stat.additions {
  color: #16a34a;
  font-family: var(--font-mono);
  font-weight: 600;
}

.stat.deletions {
  color: #dc2626;
  font-family: var(--font-mono);
  font-weight: 600;
}
</style>
