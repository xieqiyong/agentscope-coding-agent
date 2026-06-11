import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const leftSidebarOpen = ref(true)
  const rightPanelOpen = ref(false)
  const activeModal = ref<string | null>(null)
  const rightPanelTab = ref<'events' | 'timing'>('events')

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
  }
})
