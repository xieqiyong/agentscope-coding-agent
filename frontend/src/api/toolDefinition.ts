import api from './index'

export const toolDefinitionApi = {
  list(toolTypes?: string[]) {
    return api.post('/tools/list', { toolTypes: toolTypes || [] })
  },
}
