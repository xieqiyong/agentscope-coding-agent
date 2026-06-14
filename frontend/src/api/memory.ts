import api from './index'

export const memoryApi = {
  list: (workspaceId: string, userId = 'default', status = 'ALL'): Promise<any> =>
    api.post('/memories/list', { workspaceId, userId, status }),

  create: (data: { workspaceId: string; type: string; content: string; userId?: string }): Promise<any> =>
    api.post('/memories/create', { userId: 'default', ...data }),

  approve: (id: string): Promise<any> =>
    api.post('/memories/approve', { id, userId: 'default' }),

  reject: (id: string): Promise<any> =>
    api.post('/memories/reject', { id, userId: 'default' }),

  disable: (id: string): Promise<any> =>
    api.post('/memories/disable', { id, userId: 'default' }),
}
