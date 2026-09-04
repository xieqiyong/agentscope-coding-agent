import api from './index'
import type { AgentDefinition } from '@/types'

export const agentApi = {
  list: (): Promise<any> =>
    api.post('/agents/list', {}),

  create: (data: Partial<AgentDefinition> & { name: string }): Promise<any> =>
    api.post('/agents/create', data),

  update: (data: Partial<AgentDefinition> & { id: string | number }): Promise<any> =>
    api.post('/agents/update', data),
}
