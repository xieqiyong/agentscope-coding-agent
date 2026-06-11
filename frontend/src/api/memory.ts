import api from './index'
import type { MemoryEntry, MemoryStatus } from '@/types'

export const memoryApi = {
  list: (workspaceId: string): Promise<any> =>
    api.get('/memories', { params: { workspaceId } }),

  create: (data: { workspaceId: string; type: string; content: string }): Promise<any> =>
    api.post('/memories', data),

  approve: (id: string): Promise<any> =>
    api.put(`/memories/${id}/approve`),

  reject: (id: string): Promise<any> =>
    api.put(`/memories/${id}/reject`),

  disable: (id: string): Promise<any> =>
    api.put(`/memories/${id}/disable`),
}
