<template>
  <div class="workspace-select-page">
    <div class="center-card">
      <div class="card-header">
        <i class="pi pi-bolt logo-icon"></i>
        <h1>编码助手</h1>
        <p class="subtitle">选择一个工作区，开始 AI 辅助编码</p>
      </div>

      <!-- Workspace list -->
      <div v-if="workspaceStore.workspaces.length > 0" class="workspace-list">
        <div
          v-for="ws in workspaceStore.workspaces"
          :key="ws.id"
          class="workspace-card"
          @click="openWorkspace(ws.id)"
        >
          <i class="pi pi-folder workspace-icon"></i>
          <div class="workspace-info">
            <span class="workspace-name">{{ ws.name }}</span>
            <span class="workspace-path">{{ ws.rootPath }}</span>
          </div>
          <i class="pi pi-chevron-right" style="color: var(--text-muted);"></i>
        </div>
      </div>

      <div v-else class="empty-state">
        <i class="pi pi-folder-open empty-icon"></i>
        <p>还没有注册工作区。</p>
      </div>

      <!-- Register button -->
      <div class="register-section">
        <Button
          label="注册工作区"
          icon="pi pi-plus"
          @click="showRegisterDialog = true"
        />
      </div>
    </div>

    <!-- Register dialog -->
    <Dialog v-model:visible="showRegisterDialog" header="注册工作区" modal style="width: 480px">
      <div class="form-group">
        <label for="ws-name">名称</label>
        <InputText id="ws-name" v-model="registerForm.name" class="w-full" placeholder="我的项目" />
      </div>
      <div class="form-group">
        <label for="ws-path">根目录路径</label>
        <InputText id="ws-path" v-model="registerForm.rootPath" class="w-full" placeholder="/绝对路径/到/你的/项目" />
      </div>
      <div class="form-group">
        <label for="ws-desc">描述（可选）</label>
        <Textarea id="ws-desc" v-model="registerForm.description" class="w-full" rows="2" />
      </div>
      <template #footer>
        <Button label="取消" text @click="showRegisterDialog = false" />
        <Button
          label="注册"
          icon="pi pi-check"
          @click="registerWorkspace"
          :loading="registering"
          :disabled="!registerForm.name || !registerForm.rootPath"
        />
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { useWorkspaceStore } from '@/stores/workspace'

const workspaceStore = useWorkspaceStore()
const router = useRouter()

const showRegisterDialog = ref(false)
const registering = ref(false)
const registerForm = reactive({
  name: '',
  rootPath: '',
  description: '',
})

onMounted(() => {
  workspaceStore.fetchWorkspaces()
})

function openWorkspace(id: string) {
  workspaceStore.selectWorkspace(id)
  router.push({ name: 'workspace', params: { workspaceId: id } })
}

async function registerWorkspace() {
  registering.value = true
  try {
    const ws = await workspaceStore.registerWorkspace({
      name: registerForm.name,
      rootPath: registerForm.rootPath,
      description: registerForm.description || undefined,
    })
    if (ws) {
      showRegisterDialog.value = false
      registerForm.name = ''
      registerForm.rootPath = ''
      registerForm.description = ''
      router.push({ name: 'workspace', params: { workspaceId: ws.id } })
    }
  } finally {
    registering.value = false
  }
}
</script>

<style scoped>
.workspace-select-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-main);
  padding: var(--spacing-lg);
}

.center-card {
  width: 100%;
  max-width: 560px;
  background: var(--bg-panel);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  padding: var(--spacing-xl);
}

.card-header {
  text-align: center;
  margin-bottom: var(--spacing-xl);
}

.logo-icon {
  font-size: 2.5rem;
  color: var(--accent);
  margin-bottom: var(--spacing-sm);
}

h1 {
  font-size: 1.5rem;
  color: var(--text-primary);
  margin-bottom: var(--spacing-xs);
}

.subtitle {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

.workspace-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-lg);
}

.workspace-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.15s;
}

.workspace-card:hover {
  border-color: var(--accent);
  background: var(--bg-hover);
}

.workspace-icon {
  font-size: 1.5rem;
  color: var(--accent);
}

.workspace-info {
  flex: 1;
  min-width: 0;
}

.workspace-name {
  display: block;
  font-weight: 600;
  color: var(--text-primary);
}

.workspace-path {
  display: block;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-state {
  text-align: center;
  padding: var(--spacing-xl);
  color: var(--text-muted);
}

.empty-icon {
  font-size: 3rem;
  opacity: 0.2;
  margin-bottom: var(--spacing-md);
}

.register-section {
  text-align: center;
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
