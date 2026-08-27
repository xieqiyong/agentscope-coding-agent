<template>
  <div class="mcp-page">
    <header class="page-header">
      <router-link to="/" class="back-link">
        <i class="pi pi-arrow-left"></i>
        返回聊天
      </router-link>
      <div class="header-title">
        <h1>MCP 服务</h1>
        <p>登记外部 MCP 服务连接信息；接入运行时后即可绑定到 Agent 使用。</p>
      </div>
      <Button label="新建服务" icon="pi pi-plus" @click="startCreate" />
    </header>

    <main class="mcp-layout">
      <section class="mcp-list-panel">
        <div class="panel-title">
          <span>全部服务（{{ services.length }}）</span>
          <Button icon="pi pi-refresh" text size="small" :loading="loading" @click="loadServices" />
        </div>
        <div v-if="services.length === 0 && !loading" class="empty-state">
          还没有 MCP 服务，点击右上角新建一个。
        </div>
        <button
          v-for="service in services"
          :key="service.id"
          :class="['mcp-card', { active: form.id === service.id, disabled: !service.enabled }]"
          type="button"
          @click="loadService(service)"
        >
          <div class="mcp-card-main">
            <span class="mcp-avatar"><i class="pi pi-server"></i></span>
            <span class="mcp-card-copy">
              <strong>{{ service.name }}</strong>
              <small>{{ service.endpoint }}</small>
            </span>
          </div>
          <span :class="['status-chip', service.enabled ? 'on' : 'off']">
            {{ service.enabled ? '启用' : '停用' }}
          </span>
        </button>
      </section>

      <section class="mcp-editor">
        <div class="editor-section">
          <div class="section-heading">
            <h2>{{ form.id ? '编辑服务' : '创建服务' }}</h2>
            <div v-if="form.id" class="heading-actions">
              <Button
                :label="form.enabled ? '停用' : '启用'"
                severity="secondary"
                text
                size="small"
                @click="toggleEnabled"
              />
              <Button label="删除" icon="pi pi-trash" severity="danger" text size="small" @click="removeService" />
            </div>
          </div>

          <div class="form-grid">
            <label>
              <span>名称</span>
              <InputText v-model="form.name" placeholder="例如：web-search" />
            </label>
            <label>
              <span>描述</span>
              <InputText v-model="form.description" placeholder="这个服务提供什么能力" />
            </label>
            <label>
              <span>传输类型</span>
              <Select
                v-model="form.transportType"
                :options="transportOptions"
                optionLabel="label"
                optionValue="value"
                class="transport-select"
              />
            </label>
            <label>
              <span>连接地址 / 启动命令</span>
              <InputText v-model="form.endpoint" placeholder="https://example.com/mcp 或命令行" />
            </label>
          </div>
        </div>

        <div class="editor-actions">
          <Button label="重置" text @click="resetForm" />
          <Button label="保存服务" icon="pi pi-save" :loading="saving" :disabled="!form.name || !form.endpoint" @click="saveService" />
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import { mcpServiceApi, type McpServiceDefinition } from '@/api/mcpService'

const toast = useToast()
const loading = ref(false)
const saving = ref(false)
const services = ref<McpServiceDefinition[]>([])

const transportOptions = [
  { label: 'SSE', value: 'SSE' },
  { label: 'Streamable HTTP', value: 'STREAMABLE_HTTP' },
  { label: 'STDIO', value: 'STDIO' },
]

const form = reactive({
  id: '',
  name: '',
  description: '',
  transportType: 'SSE',
  endpoint: '',
  enabled: true,
})

onMounted(() => {
  loadServices()
})

async function loadServices() {
  loading.value = true
  try {
    const res: any = await mcpServiceApi.list()
    services.value = (res.data || []).map((row: any) => ({
      id: String(row.id),
      name: row.name || '',
      description: row.description || '',
      transportType: row.transportType || 'SSE',
      endpoint: row.endpoint || '',
      enabled: row.enabled !== false,
    })).filter((item: McpServiceDefinition) => item.name)
  } catch {
    services.value = []
  } finally {
    loading.value = false
  }
}

function loadService(service: McpServiceDefinition) {
  form.id = service.id
  form.name = service.name
  form.description = service.description || ''
  form.transportType = service.transportType || 'SSE'
  form.endpoint = service.endpoint
  form.enabled = service.enabled
}

function startCreate() {
  resetForm()
}

function resetForm() {
  form.id = ''
  form.name = ''
  form.description = ''
  form.transportType = 'SSE'
  form.endpoint = ''
  form.enabled = true
}

async function saveService() {
  saving.value = true
  try {
    const saved: any = await mcpServiceApi.save({
      id: form.id || undefined,
      name: form.name,
      description: form.description,
      transportType: form.transportType,
      endpoint: form.endpoint,
      enabled: form.enabled,
    })
    const row = saved?.data
    if (row?.id) {
      form.id = String(row.id)
    }
    toast.add({ severity: 'success', summary: '服务已保存', life: 1800 })
    await loadServices()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: '保存失败', detail: e.message, life: 3600 })
  } finally {
    saving.value = false
  }
}

async function toggleEnabled() {
  form.enabled = !form.enabled
  await saveService()
}

async function removeService() {
  if (!form.id) return
  try {
    await mcpServiceApi.remove(form.id)
    toast.add({ severity: 'success', summary: '服务已删除', life: 1800 })
    resetForm()
    await loadServices()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: '删除失败', detail: e.message, life: 3600 })
  }
}
</script>

<style scoped>
.mcp-page {
  height: 100vh;
  background: var(--bg-main);
  color: var(--text-primary);
  padding: 24px;
  overflow-y: auto;
  overflow-x: hidden;
}

.page-header {
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

.mcp-layout {
  max-width: 1180px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 18px;
}

.mcp-list-panel,
.mcp-editor,
.editor-section {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--bg-panel);
  box-shadow: var(--shadow-sm);
}

.mcp-list-panel {
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

.mcp-card {
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

.mcp-card:hover,
.mcp-card.active {
  background: var(--bg-hover);
}

.mcp-card.disabled .mcp-card-copy {
  opacity: 0.55;
}

.mcp-card-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.mcp-avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--ink);
  color: var(--bg-main);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.mcp-card-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mcp-card-copy small {
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-mono);
}

.status-chip {
  flex-shrink: 0;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  padding: 2px 7px;
  font-size: var(--font-size-xs);
}

.status-chip.on {
  color: var(--accent);
}

.status-chip.off {
  color: var(--text-muted);
}

.mcp-editor {
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

.heading-actions {
  display: flex;
  align-items: center;
  gap: 6px;
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

.transport-select {
  width: 100%;
}

.editor-actions {
  justify-content: flex-end;
}

.empty-state {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  padding: 18px 8px;
}

@media (max-width: 900px) {
  .mcp-layout,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
