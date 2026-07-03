<template>
  <Dialog v-model:visible="visible" header="注册工作区" modal style="width: 680px">
    <div class="form-group">
      <label for="reg-name">工作区名称</label>
      <InputText id="reg-name" v-model="form.name" class="w-full" placeholder="我的项目" />
    </div>

    <div class="form-group">
      <label>选择本地目录</label>
      <div class="path-picker">
        <div class="picker-toolbar">
          <Button label="磁盘" icon="pi pi-database" text size="small" @click="loadDirectory(null)" />
          <Button
            label="上一级"
            icon="pi pi-arrow-up"
            text
            size="small"
            :disabled="!browser.parentPath"
            @click="loadDirectory(browser.parentPath)"
          />
          <Button
            label="刷新"
            icon="pi pi-refresh"
            text
            size="small"
            :loading="browseLoading"
            @click="loadDirectory(browser.currentPath)"
          />
        </div>

        <div class="current-path">
          <span class="path-label">{{ browser.currentPath ? '当前目录' : '本地磁盘' }}</span>
          <span class="path-value">{{ browser.currentPath || '请选择一个磁盘' }}</span>
        </div>

        <div v-if="browseError" class="browse-error">{{ browseError }}</div>

        <div class="directory-list">
          <div
            v-for="entry in browser.entries"
            :key="entry.path"
            :class="['directory-row', { selected: form.rootPath === entry.path }]"
            role="button"
            tabindex="0"
            @dblclick="openEntry(entry)"
            @keydown.enter="openEntry(entry)"
          >
            <span class="directory-main" @click="openEntry(entry)">
              <i :class="entry.root ? 'pi pi-database' : 'pi pi-folder'"></i>
              <span class="directory-text">
                <span class="directory-name">{{ entry.name }}</span>
                <span class="directory-path">{{ entry.path }}</span>
              </span>
            </span>
            <span class="directory-actions">
              <Button
                v-if="!entry.root"
                label="选择"
                size="small"
                text
                @click.stop="selectPath(entry.path)"
              />
              <Button label="进入" size="small" text @click.stop="openEntry(entry)" />
            </span>
          </div>

          <div v-if="!browseLoading && browser.entries.length === 0" class="empty-list">
            当前目录没有可访问的子目录，可以直接选择当前目录。
          </div>
        </div>

        <div class="picker-footer">
          <div class="selected-path">
            <span class="path-label">已选择</span>
            <span class="path-value">{{ form.rootPath || '尚未选择目录' }}</span>
          </div>
          <Button
            label="选择当前目录"
            icon="pi pi-check"
            size="small"
            :disabled="!browser.currentPath"
            @click="selectPath(browser.currentPath)"
          />
        </div>
      </div>
      <small class="form-hint">只展示本地磁盘和目录，不需要手动输入路径。</small>
    </div>

    <div class="form-group">
      <label for="reg-desc">描述（可选）</label>
      <Textarea id="reg-desc" v-model="form.description" class="w-full" rows="2" placeholder="项目的简要描述" />
    </div>
    <template #footer>
      <Button label="取消" text @click="visible = false" />
      <Button
        label="注册"
        icon="pi pi-check"
        @click="submit"
        :loading="loading"
        :disabled="!form.name || !form.rootPath"
      />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { useWorkspaceStore } from '@/stores/workspace'
import { workspaceApi } from '@/api/workspace'
import { useToast } from 'primevue/usetoast'

interface DirectoryEntry {
  name: string
  path: string
  root?: boolean
}

const visible = defineModel<boolean>('visible', { required: true })

const workspaceStore = useWorkspaceStore()
const toast = useToast()
const loading = ref(false)
const browseLoading = ref(false)
const browseError = ref('')

const form = reactive({
  name: '',
  rootPath: '',
  description: '',
})

const browser = reactive({
  currentPath: null as string | null,
  parentPath: null as string | null,
  entries: [] as DirectoryEntry[],
})

watch(
  () => visible.value,
  (opened) => {
    if (opened && browser.entries.length === 0) {
      void loadDirectory(null)
    }
  },
)

async function loadDirectory(path: string | null) {
  browseLoading.value = true
  browseError.value = ''
  try {
    const res: any = await workspaceApi.browseDirectories({ path })
    const data = res.data || {}
    browser.currentPath = data.currentPath || null
    browser.parentPath = data.parentPath || null
    browser.entries = Array.isArray(data.entries) ? data.entries : []
  } catch (e: any) {
    browseError.value = e.message || '目录加载失败'
  } finally {
    browseLoading.value = false
  }
}

function openEntry(entry: DirectoryEntry) {
  void loadDirectory(entry.path)
}

function selectPath(path: string | null) {
  if (!path) return
  form.rootPath = path
  if (!form.name.trim()) {
    form.name = inferWorkspaceName(path)
  }
}

function inferWorkspaceName(path: string): string {
  const normalized = path.replace(/[\\/]+$/, '')
  const parts = normalized.split(/[\\/]/).filter(Boolean)
  return parts[parts.length - 1] || normalized || '工作区'
}

async function submit() {
  loading.value = true
  try {
    const ws = await workspaceStore.registerWorkspace({
      name: form.name,
      rootPath: form.rootPath,
      description: form.description || undefined,
    })
    if (ws) {
      form.name = ''
      form.rootPath = ''
      form.description = ''
      visible.value = false
      toast.add({ severity: 'success', summary: '工作区已注册', life: 2000 })
    }
  } catch (e: any) {
    toast.add({ severity: 'error', summary: '注册失败', detail: e.message, life: 4000 })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
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

.path-picker {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--bg-panel);
  overflow: hidden;
}

.picker-toolbar,
.picker-footer {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-bottom: 1px solid var(--border-color);
}

.picker-footer {
  justify-content: space-between;
  border-top: 1px solid var(--border-color);
  border-bottom: none;
}

.current-path,
.selected-path {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(237, 232, 221, 0.42);
}

.selected-path {
  flex: 1;
  padding: 0;
  background: transparent;
}

.path-label {
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-muted);
}

.path-value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: var(--text-primary);
}

.browse-error {
  padding: 8px 12px;
  color: var(--danger);
  font-size: var(--font-size-xs);
  border-top: 1px solid rgba(239, 68, 68, 0.24);
}

.directory-list {
  max-height: 320px;
  overflow-y: auto;
  padding: 6px;
}

.directory-row {
  width: 100%;
  min-height: 48px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 8px;
  cursor: pointer;
  text-align: left;
}

.directory-row:hover {
  background: var(--bg-hover);
}

.directory-row.selected {
  background: var(--accent-soft);
}

.directory-main {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
}

.directory-main i {
  flex-shrink: 0;
  color: var(--accent);
}

.directory-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.directory-name {
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.directory-path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.directory-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
}

.empty-list {
  padding: 28px 12px;
  text-align: center;
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

.form-hint {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  margin-top: 4px;
}
</style>
