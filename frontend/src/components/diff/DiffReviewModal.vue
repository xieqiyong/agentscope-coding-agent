<template>
  <Dialog
    :visible="visible"
    modal
    :closable="false"
    :maximizable="true"
    header="审查提议的修改"
    :style="{ width: '90vw', maxHeight: '85vh' }"
    :breakpoints="{ '960px': '95vw' }"
  >
    <template v-if="confirmation">
      <!-- Stats bar -->
      <DiffStats
        :files="confirmation.files"
        :risk-level="confirmation.riskLevel"
      />

      <!-- Diff files -->
      <div class="diff-files">
        <DiffFile
          v-for="file in confirmation.files"
          :key="file.path"
          :file="file"
          :diff="confirmation.diff"
        />
      </div>
    </template>

    <template #footer>
      <div class="modal-footer">
        <Button
          label="拒绝"
          icon="pi pi-times"
          severity="danger"
          outlined
          @click="onReject"
          :disabled="applying"
        />
        <Button
          label="确认并应用"
          icon="pi pi-check"
          severity="success"
          @click="onApprove"
          :loading="applying"
        />
      </div>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import DiffStats from './DiffStats.vue'
import DiffFile from './DiffFile.vue'
import type { Confirmation } from '@/types'
import { useChatStore } from '@/stores/chat'
import { useToast } from 'primevue/usetoast'

defineProps<{
  visible: boolean
  confirmation: Confirmation | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  approved: [patchId: string]
  rejected: [patchId: string]
}>()

const chatStore = useChatStore()
const toast = useToast()
const applying = ref(false)

async function onApprove() {
  if (!chatStore.pendingConfirmations[0]) return
  const patchId = chatStore.pendingConfirmations[0].patchId
  applying.value = true
  const ok = await chatStore.applyPatch(patchId)
  applying.value = false

  if (ok) {
    toast.add({ severity: 'success', summary: '已应用', detail: '修改已成功应用。', life: 3000 })
    emit('approved', patchId)
    emit('update:visible', false)
  } else {
    toast.add({ severity: 'error', summary: '应用失败', detail: 'Patch 应用失败，请重试。', life: 5000 })
  }
}

function onReject() {
  if (!chatStore.pendingConfirmations[0]) return
  const patchId = chatStore.pendingConfirmations[0].patchId
  chatStore.rejectPatch(patchId)
  toast.add({ severity: 'info', summary: '已拒绝', life: 2000 })
  emit('rejected', patchId)
  emit('update:visible', false)
}
</script>

<style scoped>
.diff-files {
  max-height: 60vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
  width: 100%;
}
</style>
