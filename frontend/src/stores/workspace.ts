import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { workspaceApi } from '@/api/workspace'
import type { Workspace, FileNode } from '@/types'

const STORAGE_WORKSPACE_ID = 'coding-agent-current-workspace-id'

export const useWorkspaceStore = defineStore('workspace', () => {
  const workspaces = ref<Workspace[]>([])
  const currentWorkspace = ref<Workspace | null>(null)
  const fileTree = ref<FileNode[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const hasWorkspace = computed(() => currentWorkspace.value !== null)

  const workspaceOptions = computed(() =>
    workspaces.value.map((w) => ({ label: w.name, value: w.id })),
  )

  async function fetchWorkspaces() {
    loading.value = true
    error.value = null
    try {
      const res: any = await workspaceApi.list()
      workspaces.value = res.data || []
    } catch (e: any) {
      error.value = e.message || '加载工作区失败'
      workspaces.value = []
    } finally {
      loading.value = false
    }
  }

  async function selectWorkspace(id: string) {
    try {
      const res: any = await workspaceApi.getById(id)
      currentWorkspace.value = res.data || null
      if (currentWorkspace.value) {
        localStorage.setItem(STORAGE_WORKSPACE_ID, String(currentWorkspace.value.id))
      }
    } catch (e: any) {
      error.value = e.message || '选择工作区失败'
      currentWorkspace.value = null
    }
  }

  async function registerWorkspace(data: { name: string; rootPath: string; description?: string }) {
    loading.value = true
    error.value = null
    try {
      const res: any = await workspaceApi.create(data)
      const newWorkspace = res.data
      workspaces.value.push(newWorkspace)
      currentWorkspace.value = newWorkspace
      localStorage.setItem(STORAGE_WORKSPACE_ID, String(newWorkspace.id))
      return newWorkspace
    } catch (e: any) {
      error.value = e.message || '注册工作区失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function updateWorkspace(id: string, data: Partial<Workspace>) {
    error.value = null
    try {
      const res: any = await workspaceApi.update(id, data)
      const updated = res.data
      const index = workspaces.value.findIndex((w) => String(w.id) === String(id))
      if (index >= 0) {
        workspaces.value[index] = updated
      }
      // 同步当前工作区，保证 TopBar 与依赖 currentWorkspace 的组件立即刷新
      if (currentWorkspace.value && String(currentWorkspace.value.id) === String(id)) {
        currentWorkspace.value = updated
      }
      return updated
    } catch (e: any) {
      error.value = e.message || '更新工作区失败'
      throw e
    }
  }

  async function deleteWorkspace(id: string) {
    error.value = null
    try {
      await workspaceApi.delete(id)
      workspaces.value = workspaces.value.filter((w) => String(w.id) !== String(id))
      // 删除的是当前工作区时清空选择，让 landing 引导用户重新选择或注册
      if (currentWorkspace.value && String(currentWorkspace.value.id) === String(id)) {
        clearWorkspace()
      }
    } catch (e: any) {
      error.value = e.message || '删除工作区失败'
      throw e
    }
  }

  async function fetchFileTree() {
    if (!currentWorkspace.value) return
    try {
      const res: any = await workspaceApi.getFileTree(currentWorkspace.value.id)
      fileTree.value = res.data || []
    } catch {
      fileTree.value = []
    }
  }

  function restoreWorkspaceId(): string | null {
    return localStorage.getItem(STORAGE_WORKSPACE_ID)
  }

  function clearWorkspace() {
    currentWorkspace.value = null
    fileTree.value = []
    localStorage.removeItem(STORAGE_WORKSPACE_ID)
  }

  return {
    workspaces,
    currentWorkspace,
    fileTree,
    loading,
    error,
    hasWorkspace,
    workspaceOptions,
    fetchWorkspaces,
    selectWorkspace,
    registerWorkspace,
    updateWorkspace,
    deleteWorkspace,
    fetchFileTree,
    restoreWorkspaceId,
    clearWorkspace,
  }
})
