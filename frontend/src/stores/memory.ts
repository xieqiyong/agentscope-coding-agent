import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { memoryApi } from '@/api/memory'
import type { MemoryEntry, MemoryStatus } from '@/types'

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
      memories.value = res.data || []
    } catch {
      memories.value = []
    } finally {
      loading.value = false
    }
  }

  async function createMemory(data: { workspaceId: string; type: string; content: string }) {
    const res: any = await memoryApi.create(data)
    const newMemory = res.data
    memories.value.push(newMemory)
    return newMemory
  }

  async function approveMemory(id: string) {
    await memoryApi.approve(id)
    const m = memories.value.find((m) => m.id === id)
    if (m) m.status = 'ACTIVE'
  }

  async function rejectMemory(id: string) {
    await memoryApi.reject(id)
    const m = memories.value.find((m) => m.id === id)
    if (m) m.status = 'REJECTED'
  }

  async function disableMemory(id: string) {
    await memoryApi.disable(id)
    const m = memories.value.find((m) => m.id === id)
    if (m) m.status = 'DISABLED'
  }

  function setFilter(f: MemoryStatus | 'ALL') {
    filter.value = f
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
