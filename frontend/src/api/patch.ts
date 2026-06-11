import api from './index'

export const patchApi = {
  apply: (patchId: string): Promise<any> =>
    api.post(`/patches/${patchId}/apply`),
}
