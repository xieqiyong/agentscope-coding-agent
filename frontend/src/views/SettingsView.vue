<template>
  <div class="settings-page">
    <div class="settings-header">
      <router-link to="/" class="back-link">
        <i class="pi pi-arrow-left"></i> 返回
      </router-link>
      <h1>设置</h1>
    </div>

    <div class="settings-body">
      <div class="settings-section">
        <h2>模型配置</h2>
        <div class="form-group">
          <label for="model-name">模型名称</label>
          <InputText id="model-name" v-model="settings.modelName" class="w-full" placeholder="例如 gpt-4o, claude-sonnet-4-6" />
        </div>
        <div class="form-group">
          <label for="base-url">API 地址</label>
          <InputText id="base-url" v-model="settings.baseUrl" class="w-full" placeholder="https://api.openai.com/v1" />
        </div>
        <div class="form-group">
          <label for="api-key">API 密钥</label>
          <InputText id="api-key" v-model="settings.apiKey" class="w-full" type="password" placeholder="sk-..." />
        </div>
        <Button label="保存设置" icon="pi pi-check" @click="saveSettings" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'

const toast = useToast()

const settings = reactive({
  modelName: localStorage.getItem('coding-agent-model') || '',
  baseUrl: localStorage.getItem('coding-agent-base-url') || '',
  apiKey: localStorage.getItem('coding-agent-api-key') || '',
})

function saveSettings() {
  localStorage.setItem('coding-agent-model', settings.modelName)
  localStorage.setItem('coding-agent-base-url', settings.baseUrl)
  localStorage.setItem('coding-agent-api-key', settings.apiKey)
  toast.add({ severity: 'success', summary: '设置已保存', life: 2000 })
}
</script>

<style scoped>
.settings-page {
  min-height: 100vh;
  background: var(--bg-main);
  padding: var(--spacing-xl);
  max-width: 640px;
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
  margin-bottom: var(--spacing-md);
}

.settings-section {
  background: var(--bg-panel);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  box-shadow: var(--shadow-sm);
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
