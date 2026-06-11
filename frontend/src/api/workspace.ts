import api from './index'
import type { Workspace, FileNode } from '@/types'

export const workspaceApi = {
  list: (): Promise<any> => api.get('/workspaces'),

  getById: (id: string): Promise<any> => api.get(`/workspaces/${id}`),

  create: (data: { name: string; rootPath: string; description?: string }): Promise<any> =>
    api.post('/workspaces', data),

  update: (id: string, data: Partial<Workspace>): Promise<any> =>
    api.put(`/workspaces/${id}`, data),

  delete: (id: string): Promise<any> => api.delete(`/workspaces/${id}`),

  getFileTree: (id: string): Promise<any> => api.get(`/workspaces/${id}/tree`),
}
