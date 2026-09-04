import api from './index'

export interface ModelConfig {
  id: number
  name: string
  provider: string
  baseUrl: string
  modelName: string
  apiKeyMask: string | null
  defaultConfig: boolean
  // 单价：美元 / 百万 token，null 表示未配置
  inputPrice?: number | null
  cachedInputPrice?: number | null
  outputPrice?: number | null
  createdAt: string
  updatedAt: string
}

export interface ModelPriceInput {
  inputPrice?: string
  cachedInputPrice?: string
  outputPrice?: string
}

export const modelConfigApi = {
  list: (): Promise<any> => api.get('/model-configs'),

  getDefault: (): Promise<any> => api.get('/model-configs/default'),

  create: (data: {
    name: string
    provider: string
    baseUrl: string
    modelName: string
    apiKey: string
  } & ModelPriceInput): Promise<any> => api.post('/model-configs', data),

  update: (id: number, data: Partial<{
    name: string
    provider: string
    baseUrl: string
    modelName: string
    apiKey: string
  } & ModelPriceInput>): Promise<any> => api.put(`/model-configs/${id}`, data),

  delete: (id: number): Promise<any> => api.delete(`/model-configs/${id}`),

  setDefault: (id: number): Promise<any> =>
    api.put(`/model-configs/${id}/set-default`),
  test: (data: {
    id?: number
    baseUrl?: string
    modelName?: string
    apiKey?: string
  }): Promise<any> => api.post('/model-configs/test', data),
}
