import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { memoryApi } from '@/api/memory'
import type { MemoryEntry, MemoryStatus, MemoryType } from '@/types'

export const useMemoryStore = defineStore('memory', () => {
  const memories = ref<MemoryEntry[]>([])
  const filter = ref<MemoryStatus | 'ALL'>('ALL')
  const loading = ref(false)

  const filteredMemories = computed(() => {
    if (filter.value === 'ALL') return memories.value
    return memories.value.filter((m) => m.status === filter.value)
  })

  const activeMemories = computed(() =>
    memories.value.filter((m) => m.status === 'ACTIVE'),
  )

  async function fetchMemories(workspaceId: string) {
    loading.value = true
    try {
      const res: any = await memoryApi.list(workspaceId)
      memories.value = normalizeMemories(res.data || [])
    } catch {
      memories.value = []
    } finally {
      loading.value = false
    }
  }

  async function createMemory(data: { workspaceId: string; type: string; content: string }) {
    const res: any = await memoryApi.create(data)
    const newMemory = normalizeMemory(res.data)
    memories.value.push(newMemory)
    return newMemory
  }

  async function approveMemory(id: string) {
    const previous = memories.value.find((m) => String(m.id) === String(id))
    const res: any = await memoryApi.approve(id)
    upsertMemory(normalizeMemory(res.data))
    if (previous?.status === 'CONFLICT' && previous.workspaceId) {
      await fetchMemories(previous.workspaceId)
    }
  }

  async function rejectMemory(id: string) {
    const res: any = await memoryApi.reject(id)
    upsertMemory(normalizeMemory(res.data))
  }

  async function disableMemory(id: string) {
    const res: any = await memoryApi.disable(id)
    upsertMemory(normalizeMemory(res.data))
  }

  function setFilter(f: MemoryStatus | 'ALL') {
    filter.value = f
  }

  function normalizeMemories(rows: unknown): MemoryEntry[] {
    return Array.isArray(rows) ? rows.map(normalizeMemory) : []
  }

  function normalizeMemory(row: any): MemoryEntry {
    return {
      id: String(row.id),
      workspaceId: String(row.workspaceId),
      agentId: row.agentId == null ? undefined : String(row.agentId),
      userId: row.userId == null ? undefined : String(row.userId),
      type: normalizeMemoryType(row.type || row.memoryType),
      memoryType: normalizeMemoryType(row.memoryType || row.type),
      normalizedKey: row.normalizedKey,
      content: String(row.content || ''),
      status: normalizeMemoryStatus(row.status),
      confidence: Number(row.confidence || 0),
      reviewReason: row.reviewReason,
      sourceConversationId: row.sourceConversationId == null ? undefined : String(row.sourceConversationId),
      sourceMessageId: row.sourceMessageId == null ? undefined : String(row.sourceMessageId),
      createdAt: row.createdAt || '',
      updatedAt: row.updatedAt || '',
    }
  }

  function upsertMemory(memory: MemoryEntry) {
    const index = memories.value.findIndex((item) => String(item.id) === String(memory.id))
    if (index >= 0) {
      memories.value[index] = memory
    } else {
      memories.value.unshift(memory)
    }
  }

  function normalizeMemoryType(value: unknown): MemoryType {
    const text = String(value || 'USER_PREFERENCE')
    if (
      text === 'USER_PREFERENCE'
      || text === 'PROJECT_FACT'
      || text === 'PROJECT_CONSTRAINT'
      || text === 'WORKING_STYLE'
      || text === 'VERIFIED_EXPERIENCE'
      || text === 'SKILL_REFERENCE'
    ) {
      return text
    }
    return 'USER_PREFERENCE'
  }

  function normalizeMemoryStatus(value: unknown): MemoryStatus {
    const text = String(value || 'PENDING')
    if (
      text === 'PENDING'
      || text === 'ACTIVE'
      || text === 'CONFLICT'
      || text === 'DISABLED'
      || text === 'REJECTED'
    ) {
      return text
    }
    return 'PENDING'
  }

  return {
    memories,
    filter,
    loading,
    filteredMemories,
    activeMemories,
    fetchMemories,
    createMemory,
    approveMemory,
    rejectMemory,
    disableMemory,
    setFilter,
  }
})
