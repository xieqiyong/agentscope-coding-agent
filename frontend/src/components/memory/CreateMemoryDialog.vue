<template>
  <Dialog v-model:visible="visible" header="添加记忆" modal style="width: 420px">
    <div class="form-group">
      <label for="mem-type">类型</label>
      <Select
        id="mem-type"
        v-model="form.type"
        :options="typeOptions"
        optionLabel="label"
        optionValue="value"
        placeholder="选择类型..."
        class="w-full"
      />
    </div>
    <div class="form-group">
      <label for="mem-content">内容</label>
      <Textarea
        id="mem-content"
        v-model="form.content"
        class="w-full"
        rows="3"
        placeholder="描述 Agent 需要记住的内容..."
      />
    </div>
    <template #footer>
      <Button label="取消" text @click="visible = false" />
      <Button
        label="添加"
        icon="pi pi-check"
        @click="create"
        :disabled="!form.type || !form.content"
      />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import { useMemoryStore } from '@/stores/memory'
import { useWorkspaceStore } from '@/stores/workspace'

const visible = defineModel<boolean>('visible', { required: true })

const memoryStore = useMemoryStore()
const workspaceStore = useWorkspaceStore()

const form = reactive({
  type: '',
  content: '',
})

const typeOptions = [
  { label: '用户偏好', value: 'USER_PREFERENCE' },
  { label: '项目事实', value: 'PROJECT_FACT' },
  { label: '项目约束', value: 'PROJECT_CONSTRAINT' },
  { label: '工作风格', value: 'WORKING_STYLE' },
  { label: '已验证经验', value: 'VERIFIED_EXPERIENCE' },
  { label: '技能参考', value: 'SKILL_REFERENCE' },
]

async function create() {
  if (!workspaceStore.currentWorkspace || !form.type || !form.content) return
  await memoryStore.createMemory({
    workspaceId: workspaceStore.currentWorkspace.id,
    type: form.type,
    content: form.content,
  })
  form.type = ''
  form.content = ''
  visible.value = false
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
</style>
