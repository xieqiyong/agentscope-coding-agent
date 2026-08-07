import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const leftSidebarOpen = ref(true)
  const rightPanelOpen = ref(false)
  const activeModal = ref<string | null>(null)
  const rightPanelTab = ref<'events' | 'timing'>('events')
  // 注册工作区对话框的全局开关，TopBar 与 ChatPanel 空状态引导都会触发它
  const registerDialogOpen = ref(false)

  function toggleLeftSidebar() {
    leftSidebarOpen.value = !leftSidebarOpen.value
  }

  function toggleRightPanel() {
    rightPanelOpen.value = !rightPanelOpen.value
  }

  function openModal(name: string) {
    activeModal.value = name
  }

  function closeModal() {
    activeModal.value = null
  }

  function setRightPanelTab(tab: 'events' | 'timing') {
    rightPanelTab.value = tab
  }

  function openRegisterDialog() {
    registerDialogOpen.value = true
  }

  function closeRegisterDialog() {
    registerDialogOpen.value = false
  }

  return {
    leftSidebarOpen,
    rightPanelOpen,
    activeModal,
    rightPanelTab,
    toggleLeftSidebar,
    toggleRightPanel,
    openModal,
    closeModal,
    setRightPanelTab,
    registerDialogOpen,
    openRegisterDialog,
    closeRegisterDialog,
  }
})
