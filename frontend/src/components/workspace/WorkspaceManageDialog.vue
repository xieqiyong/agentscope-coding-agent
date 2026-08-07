<template>
  <Dialog v-model:visible="visible" header="管理工作区" modal style="width: 640px">
    <div v-if="workspaceStore.workspaces.length === 0" class="empty-hint">
      还没有注册任何工作区，点击右下角「注册新工作区」开始。
    </div>

    <div v-else class="ws-list">
      <div
        v-for="ws in workspaceStore.workspaces"
        :key="ws.id"
        :class="['ws-card', { active: isCurrent(ws.id) }]"
      >
        <!-- 浏览态 -->
        <template v-if="!isEditing(ws.id)">
          <div class="ws-card-main">
            <span class="ws-avatar">{{ (ws.name || '?').slice(0, 1).toUpperCase() }}</span>
            <div class="ws-card-copy">
              <strong>{{ ws.name }}</strong>
              <small>{{ ws.rootPath }}</small>
            </div>
          </div>
          <div class="ws-card-actions">
            <span v-if="isCurrent(ws.id)" class="current-chip">当前</span>
            <Button label="重命名" icon="pi pi-pencil" size="small" text @click="startRename(ws)" />
            <Button label="删除" icon="pi pi-trash" size="small" text severity="danger" @click="handleDelete(ws)" />
          </div>
        </template>

        <!-- 重命名态：行内编辑，回车保存 -->
        <template v-else>
          <div class="ws-rename-row">
            <InputText v-model="editingName" class="w-full" placeholder="工作区名称" @keydown.enter="saveRename(ws)" />
            <Button label="保存" icon="pi pi-check" size="small" :loading="saving" @click="saveRename(ws)" />
            <Button label="取消" icon="pi pi-times" size="small" text @click="cancelRename" />
          </div>
        </template>
      </div>
    </div>

    <template #footer>
      <Button label="注册新工作区" icon="pi pi-plus" text @click="registerNew" />
      <Button label="关闭" text @click="visible = false" />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import { useWorkspaceStore } from '@/stores/workspace'
import { useUiStore } from '@/stores/ui'
import { useToast } from 'primevue/usetoast'
import type { Workspace } from '@/types'

const visible = defineModel<boolean>('visible', { required: true })
const workspaceStore = useWorkspaceStore()
const uiStore = useUiStore()
const toast = useToast()

// 正在重命名的工作区 id 与编辑中的名称
const editingId = ref<string | null>(null)
const editingName = ref('')
const saving = ref(false)

function isCurrent(id: string | number) {
  return (
    !!workspaceStore.currentWorkspace
    && String(workspaceStore.currentWorkspace.id) === String(id)
  )
}

function isEditing(id: string | number) {
  return editingId.value !== null && String(editingId.value) === String(id)
}

function startRename(ws: Workspace) {
  editingId.value = String(ws.id)
  editingName.value = ws.name
}

function cancelRename() {
  editingId.value = null
  editingName.value = ''
}

async function saveRename(ws: Workspace) {
  const name = editingName.value.trim()
  if (!name) return
  saving.value = true
  try {
    await workspaceStore.updateWorkspace(String(ws.id), { name })
    toast.add({ severity: 'success', summary: '已重命名', life: 1800 })
    cancelRename()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: '重命名失败', detail: e.message, life: 3600 })
  } finally {
    saving.value = false
  }
}

async function handleDelete(ws: Workspace) {
  // 后端 delete 不级联清理关联会话/记忆/Agent，这里明确告知用户后果
  const ok = window.confirm(
    `确认删除工作区「${ws.name}」？\n\n仅解除工作区登记，关联的会话、记忆和 Agent 不会被自动清理。`,
  )
  if (!ok) return
  try {
    await workspaceStore.deleteWorkspace(String(ws.id))
    toast.add({ severity: 'success', summary: '工作区已删除', life: 1800 })
  } catch (e: any) {
    toast.add({ severity: 'error', summary: '删除失败', detail: e.message, life: 3600 })
  }
}

function registerNew() {
  // 关闭管理弹窗，转而打开注册弹窗
  visible.value = false
  uiStore.openRegisterDialog()
}
</script>

<style scoped>
.empty-hint {
  text-align: center;
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  padding: var(--spacing-lg);
}

.ws-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  max-height: 60vh;
  overflow-y: auto;
  padding: var(--spacing-xs);
}

.ws-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: 10px var(--spacing-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-panel);
  transition: border-color 0.15s;
}

.ws-card.active {
  border-color: var(--success);
  background: color-mix(in srgb, var(--success) 8%, var(--bg-panel));
}

.ws-card-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.ws-avatar {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  background: var(--ink);
  color: var(--bg-main);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.ws-card-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ws-card-copy small {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ws-card-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
}

.current-chip {
  border: 1px solid var(--success);
  border-radius: 999px;
  padding: 2px 8px;
  color: var(--success);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.ws-rename-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.w-full {
  width: 100%;
}
</style>
