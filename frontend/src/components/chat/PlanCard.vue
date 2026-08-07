<template>
  <div class="plan-todo">
    <div class="plan-todo-header">
      <i class="pi pi-list-check" style="font-size: 0.72rem;"></i>
      <span class="plan-todo-title">{{ plan.title }}</span>
      <span :class="['risk-badge', plan.riskLevel.toLowerCase()]">{{ plan.riskLevel }}</span>
    </div>

    <!-- 步骤：每行 状态图标 + 步骤名 + 智能体 + 模型；完成的步骤划横线 -->
    <div class="plan-todo-steps">
      <div
        v-for="step in plan.steps"
        :key="step.id"
        :class="['plan-todo-step', step.status]"
      >
        <span class="step-icon"><i :class="stepIcon(step.status)" style="font-size: 0.66rem;"></i></span>
        <span :class="['step-title', { completed: step.status === 'completed' }]">{{ step.title }}</span>
        <span v-if="step.agentName" class="agent-pill">{{ step.agentName }}</span>
        <span v-if="step.modelName" class="agent-pill muted">{{ step.modelName }}</span>
      </div>
    </div>

    <!-- 执行按钮：仅 /plan（待执行、未在跑）才显示；普通任务自动执行时不显示 -->
    <div v-if="!disabled && isIdle" class="plan-todo-actions">
      <button class="execute-btn" type="button" @click="$emit('execute', plan)">
        <i class="pi pi-play" style="font-size: 0.7rem;"></i>
        <span>执行计划</span>
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

// 仅"待执行"（/plan 生成完、未在跑）才显示执行按钮。
// 普通任务走 PLAN_EXECUTE，整轮 isStreaming=true → disabled=true → 按钮不显示，agent 自动跑。
const isIdle = computed(() => !props.plan.executionStatus || props.plan.executionStatus === 'idle')

function stepIcon(status: PlanStep['status']): string {
  const icons: Record<PlanStep['status'], string> = {
    pending: 'pi pi-circle',
    in_progress: 'pi-spin pi-spinner',
    completed: 'pi pi-check',
    failed: 'pi pi-times',
    cancelled: 'pi pi-stop',
  }
  return icons[status] || 'pi pi-circle'
}
</script>

<style scoped>
/* 内联 todo：左侧 accent 边框 + 淡底，紧凑融入聊天流，不再是大卡片框 */
.plan-todo {
  width: min(100%, 760px);
  margin: var(--spacing-sm) 0;
  padding: var(--spacing-sm) var(--spacing-md);
  border-left: 3px solid var(--accent);
  background: color-mix(in srgb, var(--bg-hover) 50%, transparent);
  border-radius: var(--radius-sm);
}

.plan-todo-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-xs);
}

.plan-todo-title {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.risk-badge {
  flex-shrink: 0;
  font-size: 0.6rem;
  font-weight: 700;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
  padding: 1px 5px;
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

.plan-todo-steps {
  display: flex;
  flex-direction: column;
}

.plan-todo-step {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 3px 0;
  font-size: var(--font-size-xs);
  min-width: 0;
}

.step-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}
.plan-todo-step.completed .step-icon {
  color: var(--success);
}
.plan-todo-step.in_progress .step-icon {
  color: var(--accent);
}
.plan-todo-step.failed .step-icon {
  color: var(--danger);
}

.step-title {
  flex: 1;
  min-width: 0;
  color: var(--text-primary);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 执行完的步骤：划横线 + 变灰 */
.step-title.completed {
  text-decoration: line-through;
  color: var(--text-muted);
}

.agent-pill {
  flex-shrink: 0;
  border: 1px solid color-mix(in srgb, var(--accent) 34%, var(--border-color));
  border-radius: var(--radius-sm);
  padding: 1px 6px;
  color: var(--accent-hover);
  background: color-mix(in srgb, var(--accent-soft) 42%, transparent);
  font-size: 0.62rem;
  font-weight: 600;
}
.agent-pill.muted {
  color: var(--text-muted);
  border-color: var(--border-color);
  background: var(--bg-main);
}

.plan-todo-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-xs);
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
  padding: 4px 12px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.execute-btn:hover {
  background: var(--accent-hover);
  border-color: var(--accent-hover);
}
</style>
