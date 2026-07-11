import api from './index'
import type { AgentDefinition } from '@/types'

export const agentApi = {
  list: (workspaceId: string): Promise<any> =>
    api.post('/agents/list', { workspaceId }),

  create: (data: Partial<AgentDefinition> & { workspaceId: string; name: string }): Promise<any> =>
    api.post('/agents/create', data),

  update: (data: Partial<AgentDefinition> & { id: string | number }): Promise<any> =>
    api.post('/agents/update', data),
}
