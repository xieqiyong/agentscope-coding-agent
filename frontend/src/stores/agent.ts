import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { agentApi } from '@/api/agent'
import type { AgentDefinition } from '@/types'

const STORAGE_AGENT_ID = 'coding-agent-current-agent-id'

export const useAgentStore = defineStore('agent', () => {
  const agents = ref<AgentDefinition[]>([])
  const currentAgent = ref<AgentDefinition | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const agentOptions = computed(() =>
    agents.value.map((agent) => ({
      label: agent.name,
      value: String(agent.id),
    })),
  )

  async function fetchAgents(workspaceId: string) {
    loading.value = true
    error.value = null
    try {
      const res: any = await agentApi.list(workspaceId)
      agents.value = normalizeAgents(res.data || [])
      restoreOrSelectFirst()
    } catch (e: any) {
      error.value = e.message || '加载智能体失败'
      agents.value = []
      currentAgent.value = null
    } finally {
      loading.value = false
    }
  }

  function selectAgent(id: string | number | null | undefined) {
    if (id == null) return
    const found = agents.value.find((agent) => String(agent.id) === String(id))
    if (!found) return
    currentAgent.value = found
    localStorage.setItem(STORAGE_AGENT_ID, String(found.id))
  }

  async function createAgent(data: Partial<AgentDefinition> & { workspaceId: string; name: string }) {
    const res: any = await agentApi.create(data)
    const agent = normalizeAgent(res.data)
    agents.value.unshift(agent)
    currentAgent.value = agent
    localStorage.setItem(STORAGE_AGENT_ID, String(agent.id))
    return agent
  }

  async function updateAgent(data: Partial<AgentDefinition> & { id: string | number }) {
    const res: any = await agentApi.update(data)
    const agent = normalizeAgent(res.data)
    const index = agents.value.findIndex((item) => String(item.id) === String(agent.id))
    if (index >= 0) {
      agents.value[index] = agent
    } else {
      agents.value.unshift(agent)
    }
    if (currentAgent.value && String(currentAgent.value.id) === String(agent.id)) {
      currentAgent.value = agent
    }
    return agent
  }

  function restoreAgentId(): string | null {
    return localStorage.getItem(STORAGE_AGENT_ID)
  }

  function clearAgents() {
    agents.value = []
    currentAgent.value = null
  }

  function restoreOrSelectFirst() {
    const restoredId = restoreAgentId()
    const restored = agents.value.find((agent) => String(agent.id) === String(restoredId))
    currentAgent.value = restored || agents.value[0] || null
    if (currentAgent.value) {
      localStorage.setItem(STORAGE_AGENT_ID, String(currentAgent.value.id))
    }
  }

  function normalizeAgents(rows: any[]): AgentDefinition[] {
    return rows.map(normalizeAgent)
  }

  function normalizeAgent(row: any): AgentDefinition {
    return {
      id: String(row.id),
      workspaceId: String(row.workspaceId),
      name: row.name || 'Agent',
      description: row.description || '',
      systemPrompt: row.systemPrompt || '',
      skillsJson: row.skillsJson || '[]',
      mcpServicesJson: row.mcpServicesJson || '[]',
      modelConfigId: row.modelConfigId ?? null,
      maxIterations: Number(row.maxIterations || 8),
      timeoutSeconds: Number(row.timeoutSeconds || 86400),
      status: row.status || 'ENABLED',
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
    }
  }

  return {
    agents,
    currentAgent,
    loading,
    error,
    agentOptions,
    fetchAgents,
    selectAgent,
    createAgent,
    updateAgent,
    restoreAgentId,
    clearAgents,
  }
})
