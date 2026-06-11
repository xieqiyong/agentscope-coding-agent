<template>
  <div class="settings-page">
    <div class="settings-header">
      <router-link to="/" class="back-link">
        <i class="pi pi-arrow-left"></i> 返回
      </router-link>
      <h1>设置</h1>
    </div>

    <!-- 模型配置 -->
    <div class="settings-section">
      <div class="section-header">
        <h2>模型配置</h2>
        <Button label="添加模型" icon="pi pi-plus" size="small" @click="openCreateDialog" />
      </div>

      <!-- 配置列表 -->
      <div v-if="configs.length === 0" class="empty-hint">
        还没有模型配置，点击"添加模型"创建一个。
      </div>
      <div v-else class="config-list">
        <div v-for="cfg in configs" :key="cfg.id" :class="['config-card', { active: cfg.defaultConfig }]">
          <div class="config-info">
            <div class="config-name">
              {{ cfg.name }}
              <Tag v-if="cfg.defaultConfig" value="默认" severity="success" style="font-size: 0.65rem; margin-left: 6px;" />
            </div>
            <div class="config-detail">{{ cfg.provider }} · {{ cfg.modelName }}</div>
            <div class="config-detail secondary">{{ cfg.baseUrl }}</div>
            <div v-if="cfg.apiKeyMask" class="config-detail secondary">密钥：{{ cfg.apiKeyMask }}</div>
          </div>
          <div class="config-actions">
            <Button label="测试" icon="pi pi-bolt" size="small" text :loading="testingId === cfg.id" @click="testConfig(cfg)" />
            <Button v-if="!cfg.defaultConfig" label="设为默认" size="small" text @click="setDefault(cfg.id)" />
            <Button icon="pi pi-pencil" size="small" text @click="openEditDialog(cfg)" />
            <Button icon="pi pi-trash" size="small" text severity="danger" @click="deleteConfig(cfg.id)" />
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <Dialog v-model:visible="showDialog" :header="editingId ? '编辑模型配置' : '添加模型配置'" modal style="width: 500px">
      <div class="form-group">
        <label>配置名称</label>
        <InputText v-model="form.name" class="w-full" placeholder="例如：GPT-4o、DeepSeek-V3" />
      </div>
      <div class="form-group">
        <label>供应商</label>
        <Select
          v-model="form.provider"
          :options="providerOptions"
          optionLabel="label"
          optionValue="value"
          placeholder="选择供应商..."
          class="w-full"
        />
      </div>
      <div class="form-group">
        <label>模型网关地址</label>
        <InputText v-model="form.baseUrl" class="w-full" placeholder="https://api.openai.com/v1" />
      </div>
      <div class="form-group">
        <label>模型名称</label>
        <InputText v-model="form.modelName" class="w-full" placeholder="gpt-4o / deepseek-chat" />
      </div>
      <div class="form-group">
        <label>{{ editingId ? 'API 密钥（留空不修改）' : 'API 密钥' }}</label>
        <InputText v-model="form.apiKey" class="w-full" type="password" placeholder="sk-..." />
      </div>
      <template #footer>
        <Button label="测试连接" icon="pi pi-bolt" text :loading="dialogTesting" @click="testFromDialog" />
        <Button label="取消" text @click="showDialog = false" />
        <Button
          :label="editingId ? '保存' : '添加'"
          icon="pi pi-check"
          @click="save"
          :loading="saving"
        />
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import { modelConfigApi, type ModelConfig } from '@/api/modelConfig'

const toast = useToast()

const configs = ref<ModelConfig[]>([])
const showDialog = ref(false)
const saving = ref(false)
const dialogTesting = ref(false)
const testingId = ref<number | null>(null)
const editingId = ref<number | null>(null)

const form = reactive({
  name: '',
  provider: '',
  baseUrl: '',
  modelName: '',
  apiKey: '',
})

const providerOptions = [
  { label: 'OpenAI', value: 'openai' },
  { label: 'DeepSeek', value: 'deepseek' },
  { label: 'Anthropic', value: 'anthropic' },
  { label: 'Moonshot (月之暗面)', value: 'moonshot' },
  { label: 'Qwen (通义千问)', value: 'qwen' },
  { label: 'Zhipu (智谱)', value: 'zhipu' },
  { label: 'Xiaomi (小米)', value: 'xiaomi' },
  { label: 'SiliconFlow', value: 'siliconflow' },
  { label: '其他 (OpenAI Compatible)', value: 'other' },
]

onMounted(() => {
  loadConfigs()
})

async function loadConfigs() {
  try {
    const res: any = await modelConfigApi.list()
    configs.value = res.data || []
  } catch {
    configs.value = []
  }
}

function openCreateDialog() {
  editingId.value = null
  form.name = ''
  form.provider = ''
  form.baseUrl = ''
  form.modelName = ''
  form.apiKey = ''
  showDialog.value = true
}

function openEditDialog(cfg: ModelConfig) {
  editingId.value = cfg.id
  form.name = cfg.name
  form.provider = cfg.provider
  form.baseUrl = cfg.baseUrl
  form.modelName = cfg.modelName
  form.apiKey = ''
  showDialog.value = true
}

async function save() {
  saving.value = true
  try {
    if (editingId.value) {
      const data: Record<string, string> = {
        name: form.name,
        provider: form.provider,
        baseUrl: form.baseUrl,
        modelName: form.modelName,
      }
      if (form.apiKey) data.apiKey = form.apiKey
      await modelConfigApi.update(editingId.value, data)
      toast.add({ severity: 'success', summary: '已更新', life: 2000 })
    } else {
      await modelConfigApi.create({
        name: form.name,
        provider: form.provider,
        baseUrl: form.baseUrl,
        modelName: form.modelName,
        apiKey: form.apiKey,
      })
      toast.add({ severity: 'success', summary: '已添加', life: 2000 })
    }
    showDialog.value = false
    await loadConfigs()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: '保存失败', detail: e.message, life: 4000 })
  } finally {
    saving.value = false
  }
}

async function deleteConfig(id: number) {
  try {
    await modelConfigApi.delete(id)
    toast.add({ severity: 'info', summary: '已删除', life: 2000 })
    await loadConfigs()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: '删除失败', detail: e.message, life: 4000 })
  }
}

async function setDefault(id: number) {
  try {
    await modelConfigApi.setDefault(id)
    toast.add({ severity: 'success', summary: '已设为默认', life: 2000 })
    await loadConfigs()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: '设置失败', detail: e.message, life: 4000 })
  }
}

/**
 * 从弹窗表单测试模型连通性（表单里有真实密钥）。
 */
async function testFromDialog() {
  if (!form.baseUrl || !form.modelName) {
    toast.add({ severity: 'warn', summary: '请先填写网关地址和模型名称', life: 3000 })
    return
  }
  dialogTesting.value = true
  try {
    await doTestConnection(form.baseUrl, form.modelName, form.apiKey, editingId.value ?? undefined)
  } finally {
    dialogTesting.value = false
  }
}

/**
 * 从卡片测试（后端使用已保存的密钥）。
 */
async function testConfig(cfg: ModelConfig) {
  testingId.value = cfg.id
  try {
    await doTestConnection(cfg.baseUrl, cfg.modelName, undefined, cfg.id)
  } finally {
    testingId.value = null
  }
}

/**
 * 实际发送测试请求。
 */
async function doTestConnection(baseUrl: string, modelName: string, apiKey?: string, configId?: number) {
  try {
    const res: any = await modelConfigApi.test({
      id: configId,
      baseUrl,
      modelName,
      apiKey,
    })
    if (res.code && res.code !== 200) {
      toast.add({ severity: 'error', summary: '连接失败', detail: res.message, life: 6000 })
      return
    }

    const reply = res.data?.reply || '连接成功'
    const endpoint = res.data?.endpoint ? `\n接口：${res.data.endpoint}` : ''
    toast.add({ severity: 'success', summary: '连接成功', detail: `模型回复：${reply}${endpoint}`, life: 5000 })
  } catch (e: any) {
    toast.add({ severity: 'error', summary: '连接失败', detail: e.message, life: 5000 })
  }
}
</script>

<style scoped>
.settings-page {
  min-height: 100vh;
  background: var(--bg-main);
  padding: var(--spacing-xl);
  max-width: 720px;
  margin: 0 auto;
}

.settings-header {
  margin-bottom: var(--spacing-xl);
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-sm);
  color: var(--accent);
  text-decoration: none;
  margin-bottom: var(--spacing-sm);
}

.back-link:hover {
  text-decoration: underline;
}

h1 {
  font-size: 1.5rem;
  color: var(--text-primary);
}

h2 {
  font-size: var(--font-size-lg);
  color: var(--text-primary);
}

.settings-section {
  background: var(--bg-panel);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  box-shadow: var(--shadow-sm);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.empty-hint {
  text-align: center;
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  padding: var(--spacing-lg);
}

.config-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.config-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  transition: border-color 0.15s;
}

.config-card.active {
  border-color: var(--success);
  background: #f0fdf4;
}

.config-name {
  font-weight: 600;
  font-size: var(--font-size-base);
  color: var(--text-primary);
  display: flex;
  align-items: center;
}

.config-detail {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-top: 2px;
}

.config-detail.secondary {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.config-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  flex-shrink: 0;
}

.form-group {
  margin-bottom: var(--spacing-md);
}

.form-group label {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: var(--spacing-xs);
}

.w-full {
  width: 100%;
}
</style>
