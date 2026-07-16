<template>
  <div class="agents-page">
    <header class="agents-header">
      <router-link to="/" class="back-link">
        <i class="pi pi-arrow-left"></i>
        返回聊天
      </router-link>
      <div class="header-title">
        <h1>智能体</h1>
        <p>创建可选择的 Agent，控制提示词、Skills 和 MCP 服务绑定。</p>
      </div>
      <Button label="新建 Agent" icon="pi pi-plus" @click="startCreate" />
    </header>

    <main class="agents-layout">
      <section class="agent-list-panel">
        <div class="panel-title">
          <span>{{ workspaceStore.currentWorkspace?.name || '未选择工作区' }}</span>
          <Button icon="pi pi-refresh" text size="small" :loading="agentStore.loading" @click="reloadAgents" />
        </div>

        <div v-if="!workspaceStore.currentWorkspace" class="empty-state">
          先回到聊天页选择或注册工作区。
        </div>
        <div v-else-if="agentStore.agents.length === 0" class="empty-state">
          当前工作区还没有 Agent。
        </div>
        <button
          v-for="agent in agentStore.agents"
          :key="agent.id"
          :class="['agent-card', { active: form.id === agent.id }]"
          type="button"
          @click="loadAgent(agent)"
        >
          <div class="agent-card-main">
            <span class="agent-avatar">{{ agent.name.slice(0, 1).toUpperCase() }}</span>
            <span class="agent-card-copy">
              <strong>{{ agent.name }}</strong>
              <small>{{ agent.description || '无描述' }}</small>
            </span>
          </div>
          <span v-if="agentStore.currentAgent?.id === agent.id" class="current-chip">聊天中</span>
        </button>
      </section>

      <section class="agent-editor">
        <div class="editor-section">
          <div class="section-heading">
            <h2>{{ form.id ? '编辑 Agent' : '创建 Agent' }}</h2>
            <Button
              v-if="form.id"
              label="用于聊天"
              icon="pi pi-check-circle"
              text
              @click="agentStore.selectAgent(form.id)"
            />
          </div>

          <div class="form-grid">
            <label>
              <span>名称</span>
              <InputText v-model="form.name" placeholder="例如：前端专家" />
            </label>
            <label>
              <span>描述</span>
              <InputText v-model="form.description" placeholder="这个 Agent 擅长什么" />
            </label>
            <label>
              <span>最大循环次数</span>
              <input v-model.number="form.maxIterations" class="plain-input" type="number" min="1" max="30" />
            </label>
            <label>
              <span>超时秒数</span>
              <input v-model.number="form.timeoutSeconds" class="plain-input" type="number" min="30" max="86400" />
            </label>
          </div>
        </div>

        <div class="editor-section">
          <h2>系统提示词</h2>
          <Textarea
            v-model="form.systemPrompt"
            rows="9"
            autoResize
            class="full-textarea"
            placeholder="定义这个 Agent 的角色、协作方式、安全边界和输出风格。"
          />
        </div>

        <div class="editor-columns">
          <div class="editor-section">
            <div class="section-heading">
              <h2>绑定 Skills</h2>
              <Button label="添加" icon="pi pi-plus" size="small" text @click="addSkill" />
            </div>
            <div v-if="registeredSkillOptions.length" class="registered-binding">
              <span class="binding-label">已注册 Skills</span>
              <select v-model="selectedRegisteredSkills" class="multi-select" multiple>
                <option v-for="tool in registeredSkillOptions" :key="tool.id" :value="tool.name">
                  {{ tool.name }}{{ tool.description ? ` - ${tool.description}` : '' }}
                </option>
              </select>
            </div>
            <div class="binding-list">
              <div v-for="(skill, index) in skills" :key="index" class="binding-row">
                <InputText v-model="skills[index]" placeholder="skill 名称或说明" />
                <Button icon="pi pi-times" text severity="danger" @click="skills.splice(index, 1)" />
              </div>
              <div v-if="skills.length === 0" class="binding-empty">未绑定 Skills</div>
            </div>
          </div>

          <div class="editor-section">
            <div class="section-heading">
              <h2>绑定 MCP 服务</h2>
              <Button label="添加" icon="pi pi-plus" size="small" text @click="addMcpService" />
            </div>
            <div v-if="registeredMcpOptions.length" class="registered-binding">
              <span class="binding-label">已注册 MCP 服务</span>
              <select v-model="selectedRegisteredMcpServices" class="multi-select" multiple>
                <option v-for="tool in registeredMcpOptions" :key="tool.id" :value="tool.name">
                  {{ tool.name }}{{ tool.description ? ` - ${tool.description}` : '' }}
                </option>
              </select>
            </div>
            <div class="binding-list">
              <div v-for="(service, index) in mcpServices" :key="index" class="binding-row">
                <InputText v-model="mcpServices[index]" placeholder="MCP 服务名或连接说明" />
                <Button icon="pi pi-times" text severity="danger" @click="mcpServices.splice(index, 1)" />
              </div>
              <div v-if="mcpServices.length === 0" class="binding-empty">未绑定 MCP 服务</div>
            </div>
          </div>
        </div>

        <div class="editor-actions">
          <Button label="重置" text @click="resetForm" />
          <Button label="保存 Agent" icon="pi pi-save" :loading="saving" :disabled="!form.name" @click="saveAgent" />
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { useWorkspaceStore } from '@/stores/workspace'
import { useAgentStore } from '@/stores/agent'
import { toolDefinitionApi } from '@/api/toolDefinition'
import type { AgentDefinition, ToolDefinition } from '@/types'

const workspaceStore = useWorkspaceStore()
const agentStore = useAgentStore()
const toast = useToast()
const saving = ref(false)
const skills = ref<string[]>([])
const mcpServices = ref<string[]>([])
const selectedRegisteredSkills = ref<string[]>([])
const selectedRegisteredMcpServices = ref<string[]>([])
const availableTools = ref<ToolDefinition[]>([])

const form = reactive({
  id: '',
  name: '',
  description: '',
  systemPrompt: '',
  maxIterations: 8,
  timeoutSeconds: 86400,
})

const registeredSkillOptions = computed(() =>
  availableTools.value.filter((tool) => matchesToolType(tool, ['SKILL', '技能'])),
)

const registeredMcpOptions = computed(() =>
  availableTools.value.filter((tool) => matchesToolType(tool, ['MCP'])),
)

onMounted(async () => {
  if (!workspaceStore.currentWorkspace) {
    await workspaceStore.fetchWorkspaces()
    const restoredId = workspaceStore.restoreWorkspaceId()
    const workspace = workspaceStore.workspaces.find((item) => String(item.id) === String(restoredId))
      || workspaceStore.workspaces[0]
    if (workspace) {
      await workspaceStore.selectWorkspace(workspace.id)
    }
  }
  await reloadToolDefinitions()
  await reloadAgents()
})

async function reloadToolDefinitions() {
  try {
    const res: any = await toolDefinitionApi.list()
    availableTools.value = normalizeToolDefinitions(res.data || [])
  } catch {
    availableTools.value = []
  }
}

async function reloadAgents() {
  if (!workspaceStore.currentWorkspace) return
  await agentStore.fetchAgents(workspaceStore.currentWorkspace.id)
  if (agentStore.currentAgent) {
    loadAgent(agentStore.currentAgent)
  }
}

function loadAgent(agent: AgentDefinition) {
  const skillBindings = splitRegisteredBindings(parseBindingList(agent.skillsJson), registeredSkillOptions.value)
  const mcpBindings = splitRegisteredBindings(parseBindingList(agent.mcpServicesJson), registeredMcpOptions.value)
  form.id = agent.id
  form.name = agent.name
  form.description = agent.description || ''
  form.systemPrompt = agent.systemPrompt || ''
  form.maxIterations = agent.maxIterations || 8
  form.timeoutSeconds = agent.timeoutSeconds || 86400
  selectedRegisteredSkills.value = skillBindings.registered
  selectedRegisteredMcpServices.value = mcpBindings.registered
  skills.value = skillBindings.custom
  mcpServices.value = mcpBindings.custom
}

function startCreate() {
  resetForm()
  form.name = 'New Agent'
  form.description = '自定义智能体'
  form.systemPrompt = '你是一个专注、可靠的智能体。按用户目标行动，涉及工作区事实时先读取证据。'
}

function resetForm() {
  form.id = ''
  form.name = ''
  form.description = ''
  form.systemPrompt = ''
  form.maxIterations = 8
  form.timeoutSeconds = 86400
  selectedRegisteredSkills.value = []
  selectedRegisteredMcpServices.value = []
  skills.value = []
  mcpServices.value = []
}

function addSkill() {
  skills.value.push('')
}

function addMcpService() {
  mcpServices.value.push('')
}

async function saveAgent() {
  if (!workspaceStore.currentWorkspace) return
  saving.value = true
  try {
    const payload = {
      workspaceId: workspaceStore.currentWorkspace.id,
      name: form.name,
      description: form.description,
      systemPrompt: form.systemPrompt,
      skillsJson: JSON.stringify(mergeBindings(selectedRegisteredSkills.value, skills.value)),
      mcpServicesJson: JSON.stringify(mergeBindings(selectedRegisteredMcpServices.value, mcpServices.value)),
      maxIterations: form.maxIterations,
      timeoutSeconds: form.timeoutSeconds,
    }
    const saved = form.id
      ? await agentStore.updateAgent({ id: form.id, ...payload })
      : await agentStore.createAgent(payload)
    loadAgent(saved)
    toast.add({ severity: 'success', summary: 'Agent 已保存', life: 1800 })
  } catch (e: any) {
    toast.add({ severity: 'error', summary: '保存失败', detail: e.message, life: 3600 })
  } finally {
    saving.value = false
  }
}

function parseBindingList(json?: string): string[] {
  if (!json) return []
  try {
    const value = JSON.parse(json)
    if (Array.isArray(value)) {
      return value.map((item) => String(item)).filter(Boolean)
    }
  } catch {
    return [json]
  }
  return []
}

function cleanList(items: string[]) {
  return items.map((item) => item.trim()).filter(Boolean)
}

function mergeBindings(registered: string[], custom: string[]) {
  return Array.from(new Set([...cleanList(registered), ...cleanList(custom)]))
}

function splitRegisteredBindings(values: string[], registeredTools: ToolDefinition[]) {
  const registeredNames = new Set(registeredTools.map((tool) => tool.name))
  const registered: string[] = []
  const custom: string[] = []
  values.forEach((item) => {
    if (registeredNames.has(item)) {
      registered.push(item)
    } else {
      custom.push(item)
    }
  })
  return { registered, custom }
}

function normalizeToolDefinitions(rows: any[]): ToolDefinition[] {
  return rows.map((row) => ({
    id: String(row.id),
    name: row.name || '',
    description: row.description || '',
    toolType: row.toolType || '',
    inputSchemaJson: row.inputSchemaJson || '',
    riskLevel: row.riskLevel || 'LOW',
    enabled: row.enabled !== false,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
  })).filter((tool) => tool.name)
}

function matchesToolType(tool: ToolDefinition, keywords: string[]) {
  const value = `${tool.toolType || ''} ${tool.name || ''}`.toUpperCase()
  return keywords.some((keyword) => value.includes(keyword.toUpperCase()))
}
</script>

<style scoped>
.agents-page {
  height: 100vh;
  background: var(--bg-main);
  color: var(--text-primary);
  padding: 24px;
  overflow-y: auto;
  overflow-x: hidden;
}

.agents-header {
  display: flex;
  align-items: center;
  gap: 18px;
  max-width: 1180px;
  margin: 0 auto 22px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  text-decoration: none;
}

.header-title {
  flex: 1;
}

.header-title h1 {
  margin: 0;
  font-size: 1.8rem;
}

.header-title p {
  margin: 4px 0 0;
  color: var(--text-secondary);
}

.agents-layout {
  max-width: 1180px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 18px;
}

.agent-list-panel,
.agent-editor,
.editor-section {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--bg-panel);
  box-shadow: var(--shadow-sm);
}

.agent-list-panel {
  padding: 12px;
  align-self: start;
}

.panel-title,
.section-heading,
.editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  margin-bottom: 10px;
}

.agent-card {
  width: 100%;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px;
  cursor: pointer;
  text-align: left;
}

.agent-card:hover,
.agent-card.active {
  background: var(--bg-hover);
}

.agent-card-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.agent-avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--ink);
  color: var(--bg-main);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.agent-card-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.agent-card-copy small {
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.current-chip {
  flex-shrink: 0;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  padding: 2px 7px;
  color: var(--accent);
  font-size: var(--font-size-xs);
}

.agent-editor {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.editor-section {
  padding: 14px;
}

.editor-section h2 {
  margin: 0 0 12px;
  font-size: 1rem;
}

.section-heading h2 {
  margin: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.plain-input {
  width: 100%;
  height: 38px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-input);
  color: var(--text-primary);
  padding: 0 10px;
  font: inherit;
}

.full-textarea {
  width: 100%;
}

.editor-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.binding-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.registered-binding {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}

.binding-label {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.multi-select {
  width: 100%;
  min-height: 96px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-input);
  color: var(--text-primary);
  padding: 6px;
  font: inherit;
  outline: none;
}

.multi-select:focus {
  border-color: #cfc5b7;
  box-shadow: 0 0 0 2px rgba(217, 109, 74, 0.08);
}

.binding-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 6px;
}

.binding-empty,
.empty-state {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  padding: 18px 8px;
}

.editor-actions {
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .agents-layout,
  .editor-columns,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
