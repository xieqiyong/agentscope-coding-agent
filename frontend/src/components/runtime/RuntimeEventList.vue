<template>
  <div class="runtime-event-list">
    <div v-if="runtimeStore.events.length === 0" class="empty-events">
      暂无运行事件
    </div>
    <div v-for="event in runtimeStore.events" :key="event.id" :class="['event-item', event.severity]">
      <i :class="getEventIcon(event.type)" style="font-size: 0.7rem;"></i>
      <div class="event-body">
        <span class="event-label">{{ event.label }}</span>
        <span v-if="event.detail" class="event-detail">{{ event.detail }}</span>
      </div>
      <span v-if="event.durationMs" class="event-duration">{{ event.durationMs }}ms</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRuntimeStore } from '@/stores/runtime'
import type { RuntimeEventType } from '@/types/events'

const runtimeStore = useRuntimeStore()

function getEventIcon(type: RuntimeEventType): string {
  const icons: Record<string, string> = {
    RUN_STARTED: 'pi pi-play',
    AGENT_STARTED: 'pi pi-play',
    RUN_FINISHED: 'pi pi-stop',
    AGENT_FINISHED: 'pi pi-stop',
    MODEL_CALL_STARTED: 'pi pi-bolt',
    MODEL_CALL_FINISHED: 'pi pi-check',
    ANSWER_DELTA: 'pi pi-pencil',
    ANSWER_FINISHED: 'pi pi-check',
    TOOL_CALL_STARTED: 'pi pi-wrench',
    TOOL_CALL_FINISHED: 'pi pi-wrench',
    TOOL_RESULT_FINISHED: 'pi pi-check-circle',
    RUN_STATUS_CHANGED: 'pi pi-sync',
    RUNTIME_WARNING: 'pi pi-exclamation-triangle',
    CONFIRMATION_REQUIRED: 'pi pi-question-circle',
    RUN_ERROR: 'pi pi-times-circle',
  }
  return icons[type] || 'pi pi-circle'
}
</script>

<style scoped>
.runtime-event-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.empty-events {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  text-align: center;
  padding: var(--spacing-lg);
}

.event-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
}

.event-item.info { color: var(--accent); }
.event-item.success { color: var(--success); }
.event-item.warn { color: var(--warning); }
.event-item.error { color: var(--danger); }

.event-body {
  flex: 1;
  min-width: 0;
}

.event-label {
  display: block;
  font-weight: 500;
}

.event-detail {
  display: block;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.event-duration {
  font-family: var(--font-mono);
  font-size: 0.65rem;
  color: var(--text-muted);
  flex-shrink: 0;
}
</style>
