import api from './index'

/** Skill 定义（与后端 SkillEntity 对应） */
export interface SkillDefinition {
  id: string
  name: string
  description?: string
  content?: string
  enabled: boolean
  /** LOCAL 页面创建 / IMPORTED zip 导入 */
  source?: string
  /** 导入技能的解压存储目录 */
  bundlePath?: string
}

export const skillApi = {
  /** 查询技能列表；enabledOnly 为 true 时只返回启用中的技能 */
  list(enabledOnly = false) {
    return api.post('/skills/list', { enabledOnly })
  },
  /** 新建或更新技能 */
  save(payload: Partial<SkillDefinition>) {
    return api.post('/skills/save', payload)
  },
  /** 删除技能 */
  remove(id: string) {
    return api.post('/skills/delete', { id })
  },
  /** 导入 Agent Skills 标准 zip 包 */
  importZip(file: File) {
    const form = new FormData()
    form.append('file', file)
    return api.post('/skills/import', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
    })
  },
}
