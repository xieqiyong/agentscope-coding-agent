import api from './index'

/** MCP 服务定义（与后端 McpServiceEntity 对应） */
export interface McpServiceDefinition {
  id: string
  name: string
  description?: string
  transportType: string
  endpoint: string
  enabled: boolean
}

export const mcpServiceApi = {
  /** 查询 MCP 服务列表 */
  list() {
    return api.post('/mcp-services/list', {})
  },
  /** 新建或更新 MCP 服务 */
  save(payload: Partial<McpServiceDefinition>) {
    return api.post('/mcp-services/save', payload)
  },
  /** 删除 MCP 服务 */
  remove(id: string) {
    return api.post('/mcp-services/delete', { id })
  },
}
