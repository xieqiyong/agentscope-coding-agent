import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { workspaceApi } from '@/api/workspace'
import type { Workspace, FileNode } from '@/types'

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

  function clearWorkspace() {
    currentWorkspace.value = null
    fileTree.value = []
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
    clearWorkspace,
  }
})
