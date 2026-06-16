<template>
  <div class="plan-card">
    <div class="plan-header">
      <div class="plan-title-row">
        <i class="pi pi-list-check" style="font-size: 0.78rem;"></i>
        <span class="plan-title">{{ plan.title }}</span>
      </div>
      <span :class="['risk-badge', plan.riskLevel.toLowerCase()]">{{ plan.riskLevel }}</span>
    </div>

    <p v-if="plan.summary" class="plan-summary">{{ plan.summary }}</p>

    <div class="plan-steps">
      <div v-for="step in plan.steps" :key="step.id" class="plan-step">
        <span :class="['step-icon', step.status]">
          <i :class="stepIcon(step.status)" style="font-size: 0.66rem;"></i>
        </span>
        <div class="step-body">
          <div class="step-title">
            <span class="step-index">{{ step.id }}</span>
            <span>{{ step.title }}</span>
          </div>
          <div v-if="step.description" class="step-desc">{{ step.description }}</div>
          <div v-if="step.tools?.length" class="step-tools">
            <span v-for="tool in step.tools" :key="tool" class="tool-pill">{{ tool }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="plan.acceptanceCriteria.length" class="criteria">
      <span class="criteria-label">完成标准</span>
      <ul>
        <li v-for="item in plan.acceptanceCriteria" :key="item">{{ item }}</li>
      </ul>
    </div>

    <div class="plan-actions">
      <button
        class="execute-btn"
        type="button"
        :disabled="disabled || allCompleted"
        @click="$emit('execute', plan)"
      >
        <i :class="['pi', allCompleted ? 'pi-check' : 'pi-play']" style="font-size: 0.7rem;"></i>
        <span>{{ allCompleted ? '已执行' : '执行计划' }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PlanInfo, PlanStep } from '@/types'

const props = defineProps<{
  plan: PlanInfo
  disabled?: boolean
}>()

defineEmits<{
  execute: [plan: PlanInfo]
}>()

const allCompleted = computed(() =>
  props.plan.steps.length > 0 && props.plan.steps.every((step) => step.status === 'completed'),
)

function stepIcon(status: PlanStep['status']): string {
  const icons: Record<PlanStep['status'], string> = {
    pending: 'pi pi-circle',
    in_progress: 'pi pi-spin pi-spinner',
    completed: 'pi pi-check',
    failed: 'pi pi-times',
  }
  return icons[status] || 'pi pi-circle'
}
</script>

<style scoped>
.plan-card {
  width: min(100%, 760px);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-panel);
  padding: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.plan-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.plan-title-row {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.plan-title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.risk-badge {
  flex-shrink: 0;
  font-size: 0.62rem;
  font-weight: 700;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
  padding: 2px 6px;
  color: var(--text-secondary);
}

.risk-badge.high,
.risk-badge.critical {
  color: var(--danger);
  border-color: var(--danger);
}

.risk-badge.medium {
  color: var(--warning);
  border-color: var(--warning);
}

.risk-badge.low {
  color: var(--success);
  border-color: var(--success);
}

.plan-summary {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  margin: 0 0 var(--spacing-md);
}

.plan-steps {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.plan-step {
  display: flex;
  gap: var(--spacing-sm);
}

.step-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  margin-top: 2px;
}

.step-icon.completed { color: var(--success); }
.step-icon.failed { color: var(--danger); }
.step-icon.in_progress { color: var(--accent); }

.step-body {
  min-width: 0;
  flex: 1;
}

.step-title {
  display: flex;
  gap: 6px;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.step-index {
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.step-desc {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: 1.5;
  margin-top: 2px;
}

.step-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 5px;
}

.tool-pill {
  font-family: var(--font-mono);
  font-size: 0.62rem;
  color: var(--text-muted);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 1px 5px;
}

.criteria {
  margin-top: var(--spacing-md);
  border-top: 1px solid var(--border-color);
  padding-top: var(--spacing-sm);
}

.criteria-label {
  display: block;
  color: var(--text-muted);
  font-size: 0.62rem;
  font-weight: 700;
  margin-bottom: 4px;
}

.criteria ul {
  margin: 0;
  padding-left: 1.1rem;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.plan-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-md);
  border-top: 1px solid var(--border-color);
  padding-top: var(--spacing-sm);
}

.execute-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--accent);
  border-radius: var(--radius-sm);
  background: var(--accent);
  color: #fff;
  font-size: var(--font-size-xs);
  font-weight: 600;
  padding: 6px 10px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, opacity 0.15s;
}

.execute-btn:hover:not(:disabled) {
  background: var(--accent-hover);
  border-color: var(--accent-hover);
}

.execute-btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
</style>
