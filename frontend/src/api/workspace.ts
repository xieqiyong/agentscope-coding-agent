import api from './index'
import type { Workspace, FileNode } from '@/types'

export const workspaceApi = {
  list: (): Promise<any> => api.get('/workspaces'),

  getById: (id: string): Promise<any> => api.get(`/workspaces/${id}`),

  create: (data: { name: string; rootPath: string; description?: string; fromWorkspaceId?: string | number }): Promise<any> =>
    api.post('/workspaces', data),

  browseDirectories: (data: { path?: string | null }): Promise<any> =>
    api.post('/workspaces/browse-directories', data),

  update: (id: string, data: Partial<Workspace>): Promise<any> =>
    api.post(`/workspaces/${id}/update`, data),

  delete: (id: string): Promise<any> => api.post(`/workspaces/${id}/delete`),

  getFileTree: (id: string): Promise<any> => api.get(`/workspaces/${id}/tree`),
}
