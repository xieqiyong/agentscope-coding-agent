<template>
  <div class="skills-page">
    <header class="page-header">
      <router-link to="/" class="back-link">
        <i class="pi pi-arrow-left"></i>
        返回聊天
      </router-link>
      <div class="header-title">
        <h1>Skills</h1>
        <p>管理可复用的技能指令；支持导入 Agent Skills 标准 zip 包，在聊天框输入 @ 可动态挂载。</p>
      </div>
      <div class="header-actions">
        <Button label="导入 Zip" icon="pi pi-upload" severity="secondary" :loading="importing" @click="pickZip" />
        <Button label="新建 Skill" icon="pi pi-plus" @click="startCreate" />
      </div>
    </header>
    <input
      ref="zipInputEl"
      type="file"
      accept=".zip"
      class="hidden-file-input"
      @change="onZipPicked"
    />

    <main class="skills-layout">
      <section class="skill-list-panel">
        <div class="panel-title">
          <span>全部技能（{{ skills.length }}）</span>
          <Button icon="pi pi-refresh" text size="small" :loading="loading" @click="loadSkills" />
        </div>
        <div v-if="skills.length === 0 && !loading" class="empty-state">
          还没有技能，点击右上角新建一个。
        </div>
        <button
          v-for="skill in skills"
          :key="skill.id"
          :class="['skill-card', { active: form.id === skill.id, disabled: !skill.enabled }]"
          type="button"
          @click="loadSkill(skill)"
        >
          <div class="skill-card-main">
            <span class="skill-avatar"><i class="pi pi-bolt"></i></span>
            <span class="skill-card-copy">
              <strong>{{ skill.name }}</strong>
              <small>{{ skill.description || '无描述' }}</small>
            </span>
          </div>
          <span :class="['status-chip', skill.enabled ? 'on' : 'off']">
            {{ skill.enabled ? '启用' : '停用' }}
          </span>
          <span v-if="skill.source === 'IMPORTED'" class="source-chip" title="从 Agent Skills 标准 zip 包导入">zip</span>
        </button>
      </section>

      <section class="skill-editor">
        <div class="editor-section">
          <div class="section-heading">
            <h2>{{ form.id ? '编辑 Skill' : '创建 Skill' }}</h2>
            <div v-if="form.id" class="heading-actions">
              <Button
                :label="form.enabled ? '停用' : '启用'"
                severity="secondary"
                text
                size="small"
                @click="toggleEnabled"
              />
              <Button label="删除" icon="pi pi-trash" severity="danger" text size="small" @click="removeSkill" />
            </div>
          </div>

          <div class="form-grid">
            <label>
              <span>名称（@ 引用时使用）</span>
              <InputText v-model="form.name" placeholder="例如：code-review" />
            </label>
            <label>
              <span>描述</span>
              <InputText v-model="form.description" placeholder="这个技能做什么" />
            </label>
          </div>
        </div>

        <div class="editor-section">
          <h2>技能内容</h2>
          <Textarea
            v-model="form.content"
            rows="12"
            autoResize
            class="full-textarea"
            placeholder="技能正文：挂载后会作为操作指引注入本次运行的系统提示词，例如步骤、规范、检查清单。"
          />
          <p v-if="importedBundlePath" class="bundle-path">
            <i class="pi pi-folder-open"></i>
            导入包已解压到：{{ importedBundlePath }}
          </p>
        </div>

        <div class="editor-actions">
          <Button label="重置" text @click="resetForm" />
          <Button label="保存 Skill" icon="pi pi-save" :loading="saving" :disabled="!form.name" @click="saveSkill" />
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { skillApi, type SkillDefinition } from '@/api/skill'

const toast = useToast()
const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const zipInputEl = ref<HTMLInputElement | null>(null)
const skills = ref<SkillDefinition[]>([])

// 当前编辑的导入技能解压目录（仅展示）
const importedBundlePath = ref('')

const form = reactive({
  id: '',
  name: '',
  description: '',
  content: '',
  enabled: true,
})

onMounted(() => {
  loadSkills()
})

async function loadSkills() {
  loading.value = true
  try {
    const res: any = await skillApi.list()
    skills.value = normalizeSkills(res.data || [])
  } catch {
    skills.value = []
  } finally {
    loading.value = false
  }
}

function normalizeSkills(rows: any[]): SkillDefinition[] {
  return rows.map((row) => ({
    id: String(row.id),
    name: row.name || '',
    description: row.description || '',
    content: row.content || '',
    enabled: row.enabled !== false,
    source: row.source || 'LOCAL',
    bundlePath: row.bundlePath || '',
  })).filter((skill) => skill.name)
}

// 选择 zip 文件后上传导入
function pickZip() {
  zipInputEl.value?.click()
}

async function onZipPicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  importing.value = true
  try {
    const res: any = await skillApi.importZip(file)
    const skill = res?.data
    toast.add({
      severity: 'success',
      summary: '导入成功',
      detail: skill?.name ? `技能 ${skill.name} 已导入` : undefined,
      life: 2600,
    })
    await loadSkills()
    if (skill?.id) {
      loadSkill(normalizeSkills([skill])[0])
    }
  } catch (err: any) {
    toast.add({ severity: 'error', summary: '导入失败', detail: err.message, life: 5000 })
  } finally {
    importing.value = false
  }
}

function loadSkill(skill: SkillDefinition) {
  form.id = skill.id
  form.name = skill.name
  form.description = skill.description || ''
  form.content = skill.content || ''
  form.enabled = skill.enabled
  importedBundlePath.value = skill.bundlePath || ''
}

function startCreate() {
  resetForm()
}

function resetForm() {
  form.id = ''
  form.name = ''
  form.description = ''
  form.content = ''
  form.enabled = true
}

async function saveSkill() {
  saving.value = true
  try {
    const saved: any = await skillApi.save({
      id: form.id || undefined,
      name: form.name,
      description: form.description,
      content: form.content,
      enabled: form.enabled,
    })
    const row = saved?.data
    if (row?.id) {
      form.id = String(row.id)
    }
    toast.add({ severity: 'success', summary: 'Skill 已保存', life: 1800 })
    await loadSkills()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: '保存失败', detail: e.message, life: 3600 })
  } finally {
    saving.value = false
  }
}

async function toggleEnabled() {
  form.enabled = !form.enabled
  await saveSkill()
}

async function removeSkill() {
  if (!form.id) return
  try {
    await skillApi.remove(form.id)
    toast.add({ severity: 'success', summary: 'Skill 已删除', life: 1800 })
    resetForm()
    await loadSkills()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: '删除失败', detail: e.message, life: 3600 })
  }
}
</script>

<style scoped>
.skills-page {
  height: 100vh;
  background: var(--bg-main);
  color: var(--text-primary);
  padding: 24px;
  overflow-y: auto;
  overflow-x: hidden;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 18px;
  max-width: 1180px;
  margin: 0 auto 22px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  text-decoration: none;
}

.header-title {
  flex: 1;
}

.header-title h1 {
  margin: 0;
  font-size: 1.8rem;
}

.header-title p {
  margin: 4px 0 0;
  color: var(--text-secondary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hidden-file-input {
  display: none;
}

.source-chip {
  flex-shrink: 0;
  border-radius: 6px;
  padding: 2px 6px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-family: var(--font-mono);
}

.bundle-path {
  margin: 10px 0 0;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  font-family: var(--font-mono);
  word-break: break-all;
  display: flex;
  align-items: center;
  gap: 6px;
}

.skills-layout {
  max-width: 1180px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 18px;
}

.skill-list-panel,
.skill-editor,
.editor-section {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--bg-panel);
  box-shadow: var(--shadow-sm);
}

.skill-list-panel {
  padding: 12px;
  align-self: start;
}

.panel-title,
.section-heading,
.editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  margin-bottom: 10px;
}

.skill-card {
  width: 100%;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px;
  cursor: pointer;
  text-align: left;
}

.skill-card:hover,
.skill-card.active {
  background: var(--bg-hover);
}

.skill-card.disabled .skill-card-copy {
  opacity: 0.55;
}

.skill-card-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.skill-avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--ink);
  color: var(--bg-main);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.skill-card-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.skill-card-copy small {
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-chip {
  flex-shrink: 0;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  padding: 2px 7px;
  font-size: var(--font-size-xs);
}

.status-chip.on {
  color: var(--accent);
}

.status-chip.off {
  color: var(--text-muted);
}

.skill-editor {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.editor-section {
  padding: 14px;
}

.editor-section h2 {
  margin: 0 0 12px;
  font-size: 1rem;
}

.section-heading h2 {
  margin: 0;
}

.heading-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.full-textarea {
  width: 100%;
}

.editor-actions {
  justify-content: flex-end;
}

.empty-state {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  padding: 18px 8px;
}

@media (max-width: 900px) {
  .skills-layout,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
