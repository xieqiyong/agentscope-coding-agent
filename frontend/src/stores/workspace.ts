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
    fetchFileTree,
    restoreWorkspaceId,
    clearWorkspace,
  }
})
