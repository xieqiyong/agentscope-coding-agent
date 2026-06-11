<template>
  <Dialog v-model:visible="visible" header="注册工作区" modal style="width: 480px">
    <div class="form-group">
      <label for="reg-name">工作区名称</label>
      <InputText id="reg-name" v-model="form.name" class="w-full" placeholder="我的项目" />
    </div>
    <div class="form-group">
      <label for="reg-path">根目录路径（绝对路径）</label>
      <InputText id="reg-path" v-model="form.rootPath" class="w-full" placeholder="/路径/到/你的/项目" />
      <small class="form-hint">必须是本地目录的绝对路径</small>
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
import { reactive, ref } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { useWorkspaceStore } from '@/stores/workspace'
import { useToast } from 'primevue/usetoast'

const visible = defineModel<boolean>('visible', { required: true })

const workspaceStore = useWorkspaceStore()
const toast = useToast()
const loading = ref(false)

const form = reactive({
  name: '',
  rootPath: '',
  description: '',
})

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

.form-hint {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  margin-top: 2px;
}
</style>
