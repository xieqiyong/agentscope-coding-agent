import api from './index'
import type { Session, ChatMessage } from '@/types'

export const chatApi = {
  listSessions: (workspaceId: string): Promise<any> =>
    api.get('/sessions', { params: { workspaceId } }),

  createSession: (data: { workspaceId: string; title?: string }): Promise<any> =>
    api.post('/sessions', data),

  getSession: (id: string): Promise<any> => api.get(`/sessions/${id}`),

  deleteSession: (id: string): Promise<any> => api.delete(`/sessions/${id}`),

  listMessages: (sessionId: string): Promise<any> =>
    api.get(`/sessions/${sessionId}/messages`),

  getTimeline: (sessionId: string): Promise<any> =>
    api.post(`/sessions/${sessionId}/timeline`),
}
