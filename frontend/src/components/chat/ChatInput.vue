<template>
  <div :class="['chat-input-area', variant]">
    <div class="input-container">
      <div class="input-wrapper" :class="{ focused: isFocused }">
        <!-- 已挂载的 Skills 提示条 -->
        <div v-if="mountedSkills.length" class="mounted-chips">
          <span
            v-for="skill in mountedSkills"
            :key="skill"
            class="mounted-chip"
            title="本次运行挂载的 Skill"
          >
            <i class="pi pi-bolt" style="font-size: 0.6rem;"></i>
            {{ skill }}
            <button class="chip-remove" type="button" @click="unmountSkill(skill)">
              <i class="pi pi-times" style="font-size: 0.55rem;"></i>
            </button>
          </span>
        </div>
        <textarea
          ref="textareaEl"
          v-model="inputText"
          class="chat-textarea"
          :placeholder="placeholder"
          :disabled="disabled"
          @keydown="onKeydown"
          @focus="isFocused = true"
          @blur="onBlur"
          @input="onInput"
          rows="1"
        />
        <!-- @ 技能选择弹层 -->
        <div v-if="mentionState.open" class="mention-popover">
          <div class="mention-title">挂载 Skill（只对本次对话生效）</div>
          <div v-if="mentionState.filtered.length === 0" class="mention-empty">
            没有匹配的技能，可在左侧 Skills 页面创建。
          </div>
          <button
            v-for="skill in mentionState.filtered"
            :key="skill.name"
            class="mention-option"
            type="button"
            @mousedown.prevent="selectMention(skill)"
          >
            <i class="pi pi-bolt"></i>
            <span class="mention-name">{{ skill.name }}</span>
            <span class="mention-desc">{{ skill.description || '无描述' }}</span>
          </button>
        </div>
        <div class="input-actions">
          <button class="utility-btn add" type="button" title="添加上下文">
            <i class="pi pi-plus"></i>
          </button>
          <div class="action-spacer"></div>
          <button class="utility-btn" type="button" title="语音输入">
            <i class="pi pi-microphone"></i>
          </button>
          <button class="utility-btn" type="button" title="工具">
            <i class="pi pi-sliders-h"></i>
          </button>
          <button
            v-if="chatStore.isStreaming"
            class="action-btn stop"
            @click="sse.abort()"
            title="停止生成"
          >
            <i class="pi pi-stop" style="font-size: 0.75rem;"></i>
          </button>
          <button
            v-else
            class="action-btn send"
            :disabled="!canSend"
            @click="send"
            title="发送 (Enter)"
          >
            <i class="pi pi-arrow-up" style="font-size: 0.8rem;"></i>
          </button>
        </div>
      </div>

      <!-- 底部信息栏 -->
      <div v-if="variant !== 'landing'" class="input-footer">
        <div class="footer-left"></div>
        <div class="footer-right">
          <span class="footer-hint">Enter 发送 · Shift+Enter 换行</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, reactive } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useWorkspaceStore } from '@/stores/workspace'
import { useAgentStore } from '@/stores/agent'
import { useSse } from '@/composables/useSse'
import { skillApi, type SkillDefinition } from '@/api/skill'

const chatStore = useChatStore()
const workspaceStore = useWorkspaceStore()
const agentStore = useAgentStore()
const sse = useSse()

withDefaults(defineProps<{
  variant?: 'landing' | 'dock'
}>(), {
  variant: 'dock',
})

const inputText = ref('')
const textareaEl = ref<HTMLTextAreaElement | null>(null)
const isFocused = ref(false)

// 可 @ 挂载的技能（只加载启用中的）
const availableSkills = ref<SkillDefinition[]>([])
// 本次运行要动态挂载的技能名称
const mountedSkills = ref<string[]>([])
// @ 引用弹层状态：open 是否展开、query 关键字、anchor 光标位置、filtered 过滤结果
const mentionState = reactive<{
  open: boolean
  query: string
  anchor: number
  filtered: SkillDefinition[]
}>({
  open: false,
  query: '',
  anchor: 0,
  filtered: [],
})

const canSend = computed(
  () => inputText.value.trim().length > 0 && workspaceStore.currentWorkspace && agentStore.currentAgent && !chatStore.isStreaming,
)

const disabled = computed(
  () => !workspaceStore.currentWorkspace || !agentStore.currentAgent || chatStore.isStreaming,
)

const placeholder = computed(() => {
  if (!workspaceStore.currentWorkspace) return '请先选择一个工作区...'
  if (!agentStore.currentAgent) return '请先选择或创建一个 Agent...'
  if (chatStore.isStreaming) return 'Agent 正在思考...'
  return 'How can I help you today?'
})

// 加载可 @ 挂载的技能列表
onMounted(async () => {
  try {
    const res: any = await skillApi.list(true)
    availableSkills.value = (res.data || [])
      .map((row: any) => ({
        id: String(row.id),
        name: row.name || '',
        description: row.description || '',
        content: row.content || '',
        enabled: row.enabled !== false,
      }))
      .filter((skill: SkillDefinition) => skill.name)
  } catch {
    availableSkills.value = []
  }
})

function onKeydown(e: KeyboardEvent) {
  if (mentionState.open && (e.key === 'Escape')) {
    mentionState.open = false
    e.preventDefault()
    return
  }
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function onInput(e: Event) {
  autoResize()
  const el = e.target as HTMLTextAreaElement
  detectMention(el)
}

// 失焦时关闭弹层；mousedown.prevent 已保证点击选项不触发失焦
function onBlur() {
  isFocused.value = false
  mentionState.open = false
}

// 检测光标前是否正在输入 @ 引用，是则打开技能选择弹层
function detectMention(el: HTMLTextAreaElement) {
  const caret = el.selectionStart ?? inputText.value.length
  const before = inputText.value.slice(0, caret)
  const match = before.match(/@([\w\-.]*)$/)
  if (!match) {
    mentionState.open = false
    return
  }
  mentionState.query = match[1]
  mentionState.anchor = caret - match[0].length
  const query = mentionState.query.toLowerCase()
  const list = query
    ? availableSkills.value.filter((skill) =>
        skill.name.toLowerCase().includes(query)
        || (skill.description || '').toLowerCase().includes(query))
    : availableSkills.value
  mentionState.filtered = list.slice(0, 8)
  mentionState.open = mentionState.filtered.length > 0
}

// 选中技能：替换 @ 关键字为完整名称，并把技能加入本次挂载列表
function selectMention(skill: SkillDefinition) {
  const caret = textareaEl.value?.selectionStart ?? inputText.value.length
  const before = inputText.value.slice(0, mentionState.anchor)
  const after = inputText.value.slice(caret)
  inputText.value = `${before}@${skill.name} ${after}`
  if (!mountedSkills.value.includes(skill.name)) {
    mountedSkills.value.push(skill.name)
  }
  mentionState.open = false
  nextTick(() => {
    const pos = (before + `@${skill.name} `).length
    textareaEl.value?.focus()
    textareaEl.value?.setSelectionRange(pos, pos)
    autoResize()
  })
}

function unmountSkill(name: string) {
  mountedSkills.value = mountedSkills.value.filter((item) => item !== name)
}

async function send() {
  const text = inputText.value.trim()
  if (!text || !canSend.value) return
  const command = parseSlashCommand(text)

  chatStore.addUserMessage(text)
  inputText.value = ''

  await nextTick()
  autoResize()

  // 模型配置由所选 Agent 绑定的模型决定，前端不再传模型参数
  // @ 挂载的技能只对本次运行生效，发送后即卸载
  const mountedSkillNames = [...mountedSkills.value]
  const ws = workspaceStore.currentWorkspace!
  const agent = agentStore.currentAgent!
  sse.start({
    workspaceId: Number(ws.id),
    conversationId: chatStore.lastConversationId ?? undefined,
    message: command.message,
    runMode: command.runMode || 'AUTO',
    agentId: Number(agent.id),
    userId: '1',
    timeoutSeconds: 86400,
    mountedSkills: mountedSkillNames,
  })
  mountedSkills.value = []
}

function parseSlashCommand(text: string): { message: string; runMode?: string } {
  const trimmed = text.trim()
  if (trimmed.startsWith('/plan')) {
    const task = trimmed.slice('/plan'.length).trim()
    return {
      message: task || trimmed,
      runMode: task ? 'PLAN_ONLY' : undefined,
    }
  }
  return { message: trimmed }
}

function autoResize() {
  const el = textareaEl.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 200) + 'px'
  }
}
</script>

<style scoped>
.chat-input-area {
  background: var(--bg-main);
  padding: var(--spacing-md) var(--spacing-lg);
  flex-shrink: 0;
}

.chat-input-area.dock {
  border-top: 1px solid rgba(223, 216, 204, 0.72);
}

.chat-input-area.landing {
  width: min(100%, 840px);
  padding: 0;
  background: transparent;
}

.input-container {
  max-width: 840px;
  margin: 0 auto;
}

.input-wrapper {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--spacing-sm);
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 24px;
  padding: 20px 22px 16px;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-shadow: 0 6px 16px rgba(47, 42, 36, 0.075);
}

/* 已挂载 Skills 的提示条 */
.mounted-chips {
  width: 100%;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mounted-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--bg-hover);
  color: var(--accent);
  font-size: var(--font-size-xs);
  padding: 3px 6px 3px 9px;
}

.chip-remove {
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
}

.chip-remove:hover {
  background: rgba(0, 0, 0, 0.08);
  color: var(--text-primary);
}

/* @ 技能选择弹层 */
.mention-popover {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: calc(100% + 6px);
  max-height: 260px;
  overflow-y: auto;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: 0 10px 28px rgba(47, 42, 36, 0.16);
  padding: 6px;
  z-index: 30;
}

.mention-title {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  padding: 6px 8px 4px;
}

.mention-empty {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  padding: 8px;
}

.mention-option {
  width: 100%;
  border: none;
  background: transparent;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  cursor: pointer;
  color: var(--text-primary);
  text-align: left;
  font: inherit;
}

.mention-option:hover {
  background: var(--bg-hover);
}

.mention-option .pi {
  color: var(--accent);
  flex-shrink: 0;
}

.mention-name {
  font-weight: 600;
  flex-shrink: 0;
}

.mention-desc {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.input-wrapper.focused {
  border-color: #cfc5b7;
  box-shadow: 0 8px 22px rgba(47, 42, 36, 0.11);
}

.chat-textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  font-family: inherit;
  font-size: var(--font-size-base);
  line-height: 1.5;
  color: var(--text-primary);
  background: transparent;
  max-height: 200px;
  min-height: 54px;
}

.chat-textarea::placeholder {
  color: var(--text-muted);
}

.chat-textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-actions {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.action-spacer {
  flex: 1;
}

.utility-btn {
  height: 34px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--ink);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.utility-btn {
  width: 34px;
  font-size: var(--font-size-sm);
}

.utility-btn.add {
  font-size: 1rem;
}

.utility-btn:hover {
  background: var(--bg-hover);
}

.action-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.action-btn.send {
  background: var(--ink);
  color: white;
}

.action-btn.send:hover:not(:disabled) {
  background: #000;
}

.action-btn.send:disabled {
  opacity: 0.3;
  cursor: not-allowed;
  background: var(--text-muted);
}

.action-btn.stop {
  background: var(--danger);
  color: white;
}

.action-btn.stop:hover {
  background: #dc2626;
}

/* 底部信息栏 */
.input-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--spacing-xs);
  padding: 0 var(--spacing-xs);
}

.footer-hint {
  font-size: 0.65rem;
  color: var(--text-muted);
}

.footer-left,
.footer-right {
  display: flex;
  align-items: center;
}

.chat-input-area.dock .input-wrapper {
  border-radius: 18px;
  padding: 12px 14px 10px;
  box-shadow: 0 4px 12px rgba(47, 42, 36, 0.055);
}

.chat-input-area.dock .chat-textarea {
  min-height: 28px;
}

@media (max-width: 760px) {
  .chat-input-area {
    padding: 12px;
  }

  .input-wrapper {
    border-radius: 18px;
    padding: 16px;
  }

  .utility-btn:nth-of-type(3) {
    display: none;
  }
}
</style>
