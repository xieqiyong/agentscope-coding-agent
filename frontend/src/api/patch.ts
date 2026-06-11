import api from './index'

export const patchApi = {
  get: (patchId: string): Promise<any> =>
    api.get(`/patches/${patchId}`),

  apply: (patchId: string): Promise<any> =>
    api.post(`/patches/${patchId}/apply`),
}
