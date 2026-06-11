<template>
  <div :class="['memory-item', memory.status.toLowerCase()]">
    <div class="memory-type-badge">{{ formatType(memory.type) }}</div>
    <div class="memory-content">{{ memory.content }}</div>
    <div class="memory-meta">
      <span class="memory-confidence">{{ Math.round(memory.confidence * 100) }}%</span>
      <span class="memory-status-badge" :class="memory.status.toLowerCase()">{{ memory.status }}</span>
    </div>
    <div class="memory-actions" v-if="memory.status === 'CANDIDATE' || memory.status === 'CONFLICT'">
      <button class="action-btn approve" @click="$emit('approve')" title="批准">
        <i class="pi pi-check"></i>
      </button>
      <button class="action-btn reject" @click="$emit('reject')" title="拒绝">
        <i class="pi pi-times"></i>
      </button>
    </div>
    <div class="memory-actions" v-else-if="memory.status === 'ACTIVE'">
      <button class="action-btn disable" @click="$emit('disable')" title="禁用">
        <i class="pi pi-ban"></i>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { MemoryEntry } from '@/types'

defineProps<{
  memory: MemoryEntry
}>()

defineEmits<{
  approve: []
  reject: []
  disable: []
}>()

function formatType(type: string): string {
  return type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())
}
</script>

<style scoped>
.memory-item {
  padding: var(--spacing-sm);
  border-radius: var(--radius-sm);
  border-left: 3px solid var(--text-muted);
  margin-bottom: var(--spacing-xs);
  background: var(--bg-panel);
}

.memory-item.active { border-left-color: var(--success); }
.memory-item.candidate { border-left-color: var(--accent); }
.memory-item.conflict { border-left-color: var(--warning); }
.memory-item.disabled { border-left-color: var(--text-muted); opacity: 0.5; }
.memory-item.rejected { border-left-color: var(--danger); opacity: 0.5; }

.memory-type-badge {
  font-size: 0.6rem;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--text-muted);
  letter-spacing: 0.3px;
  margin-bottom: 2px;
}

.memory-content {
  font-size: var(--font-size-xs);
  color: var(--text-primary);
  line-height: 1.4;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.memory-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  margin-top: 4px;
}

.memory-confidence {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  color: var(--text-muted);
}

.memory-status-badge {
  font-size: 0.6rem;
  font-weight: 600;
  text-transform: uppercase;
  padding: 0 4px;
  border-radius: 2px;
}

.memory-status-badge.active { color: #166534; }
.memory-status-badge.candidate { color: #1e40af; }
.memory-status-badge.conflict { color: #92400e; }
.memory-status-badge.disabled { color: var(--text-muted); }

.memory-actions {
  display: flex;
  gap: 4px;
  margin-top: 4px;
}

.action-btn {
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.6rem;
}

.action-btn.approve { background: #dcfce7; color: #166534; }
.action-btn.reject { background: #fee2e2; color: #991b1b; }
.action-btn.disable { background: var(--bg-hover); color: var(--text-muted); }

.action-btn:hover { opacity: 0.8; }
</style>
