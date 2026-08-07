<template>
  <div :class="['chat-panel', { 'landing-mode': isLanding }]">
    <div v-if="isLanding" class="landing-shell">
      <div class="greeting">
        <span class="greeting-mark">✳</span>
        <h1>{{ greetingTitle }}</h1>
      </div>

      <!-- 无工作区：引导注册，不展示禁用的输入框和纯装饰 chip -->
      <div v-if="!workspaceStore.hasWorkspace" class="onboarding-cta">
        <p class="cta-desc">工作区是 Agent 读取、搜索和修改代码的根目录。注册一个本地项目目录即可开始。</p>
        <Button
          label="注册第一个工作区"
          icon="pi pi-folder-plus"
          size="large"
          class="cta-btn"
          @click="uiStore.openRegisterDialog()"
        />
      </div>

      <!-- 有工作区但无消息：展示输入框即可 -->
      <template v-else>
        <ChatInput variant="landing" />
      </template>
    </div>

    <div v-else class="chat-messages" ref="messagesContainer" @scroll.passive="handleMessagesScroll">
      <MessageList
        @review-confirmation="$emit('reviewConfirmation', $event)"
        @approve-confirmation="handleToolApproval($event, true)"
        @reject-confirmation="handleToolApproval($event, false)"
        @execute-plan="handleExecutePlan"
      />
    </div>

    <!-- 输入栏 -->
    <ChatInput v-if="!isLanding" variant="dock" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, nextTick, watch } from 'vue'
import MessageList from '@/components/chat/MessageList.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import Button from 'primevue/button'
import { useChatStore } from '@/stores/chat'
import { useWorkspaceStore } from '@/stores/workspace'
import { useUiStore } from '@/stores/ui'
import { useAgentStore } from '@/stores/agent'
import { useSse } from '@/composables/useSse'
import { modelConfigApi } from '@/api/modelConfig'
import type { Confirmation, PlanInfo, PlanStep } from '@/types'

defineEmits<{
  reviewConfirmation: [confirmation: Confirmation]
}>()

const chatStore = useChatStore()
const workspaceStore = useWorkspaceStore()
const uiStore = useUiStore()
const agentStore = useAgentStore()
const sse = useSse()
const messagesContainer = ref<HTMLElement | null>(null)
const autoScrollEnabled = ref(true)
const executingPlanKeys = ref<Set<string>>(new Set())
const BOTTOM_THRESHOLD = 96

const isLanding = computed(() => (
  !workspaceStore.hasWorkspace
  || (chatStore.messages.length === 0 && !chatStore.isStreaming)
))

const greetingTitle = computed(() => {
  if (!workspaceStore.hasWorkspace) return 'Choose a workspace'
  const hour = new Date().getHours()
  const period = hour < 12 ? 'Morning' : hour < 18 ? 'Afternoon' : 'Evening'
  return `${period}, admin`
})

// 用户在底部附近时才跟随流式输出；上滑查看历史时不抢滚动位置。
function scrollToBottom(force = false) {
  nextTick(() => {
    const container = messagesContainer.value
    if (!container) {
      return
    }
    if (force || autoScrollEnabled.value || isNearBottom(container)) {
      container.scrollTop = container.scrollHeight
      autoScrollEnabled.value = true
    }
  })
}

function handleMessagesScroll() {
  const container = messagesContainer.value
  if (!container) {
    return
  }
  autoScrollEnabled.value = isNearBottom(container)
}

function isNearBottom(container: HTMLElement) {
  return container.scrollHeight - container.scrollTop - container.clientHeight <= BOTTOM_THRESHOLD
}

// 新消息、流式文本、思考和工具轨迹变化时自动滚动到底部。
watch(
  () => chatStore.messages.map((message) => [
    message.id,
    message.content?.length || 0,
    message.isStreaming ? 'streaming' : 'done',
    message.thinking?.status || '',
    message.thinking?.chars || 0,
    message.thinking?.content?.length || 0,
    (message.toolCalls || []).map((toolCall) => [
      toolCall.callId,
      toolCall.status,
      toolCall.argsText?.length || 0,
      JSON.stringify(toolCall.args || {}).length,
      toolCall.result?.length || 0,
    ].join(',')).join(';'),
  ].join(':')).join('|'),
  () => scrollToBottom(),
)

async function handleToolApproval(confirmation: Confirmation, approved: boolean) {
  if (confirmation.kind !== 'TOOL_PERMISSION') return
  await sse.respondApproval(confirmation, approved)
}

async function handleExecutePlan(plan: PlanInfo) {
  if (!workspaceStore.currentWorkspace || !agentStore.currentAgent || chatStore.isStreaming) return
  const planKey = buildPlanKey(plan)
  if (executingPlanKeys.value.has(planKey) || plan.executionStatus === 'running' || plan.executionStatus === 'completed') {
    return
  }
  executingPlanKeys.value.add(planKey)
  plan.executionStatus = 'running'

  const message = buildPlanExecutionMessage(plan)
  chatStore.addUserMessage(message, { messageKind: 'plan-execute' })

  try {
    const modelConfig = await loadModelConfig()
    const ws = workspaceStore.currentWorkspace
    const agent = agentStore.currentAgent
    await sse.start({
      workspaceId: Number(ws.id),
      conversationId: chatStore.lastConversationId ?? undefined,
      message,
      plan,
      runMode: 'PLAN_EXECUTE',
      agentId: Number(agent.id),
      userId: '1',
      timeoutSeconds: 86400,
      modelBaseUrl: modelConfig.modelBaseUrl,
      modelName: modelConfig.modelName,
      apiKey: modelConfig.apiKey,
    })
  } finally {
    executingPlanKeys.value.delete(planKey)
    if (plan.executionStatus === 'running' && plan.steps.every((step) => step.status !== 'in_progress')) {
      plan.executionStatus = plan.steps.some((step) => step.status === 'failed') ? 'failed' : 'idle'
    }
  }
}

async function loadModelConfig(): Promise<{
  modelBaseUrl?: string
  modelName?: string
  apiKey?: string
}> {
  try {
    const res: any = await modelConfigApi.getDefault()
    const cfg = res.data
    return {
      modelBaseUrl: cfg?.baseUrl,
      modelName: cfg?.modelName,
      apiKey: cfg?.apiKeyCipher,
    }
  } catch {
    return {
      modelBaseUrl: localStorage.getItem('coding-agent-base-url') || undefined,
      modelName: localStorage.getItem('coding-agent-model') || undefined,
      apiKey: localStorage.getItem('coding-agent-api-key') || undefined,
    }
  }
}

function buildPlanExecutionMessage(plan: PlanInfo): string {
  const lines: string[] = [
    `执行计划：${plan.title}`,
    '',
  ]
  if (plan.summary) {
    lines.push(`摘要：${plan.summary}`, '')
  }
  lines.push(`风险等级：${plan.riskLevel}`, '')
  lines.push('计划步骤：')
  plan.steps.forEach((step, index) => {
    lines.push(formatPlanStep(step, index))
  })
  if (plan.acceptanceCriteria.length > 0) {
    lines.push('', '完成标准：')
    plan.acceptanceCriteria.forEach((item, index) => {
      lines.push(`${index + 1}. ${item}`)
    })
  }
  lines.push(
    '',
    '请作为 ExecutorAgent 按上面的计划执行。执行前仍要读取必要文件和证据；如果计划与代码事实冲突，以实际证据为准并说明调整原因。',
  )
  return lines.join('\n')
}

function formatPlanStep(step: PlanStep, index: number): string {
  const parts = [`${step.id || index + 1}. ${step.title}`]
  if (step.description) {
    parts.push(`   说明：${step.description}`)
  }
  if (step.tools.length > 0) {
    parts.push(`   预期工具：${step.tools.join(', ')}`)
  }
  if (step.agentName || step.agentRole || step.agentId) {
    const agentParts = [
      step.agentName ? `名称=${step.agentName}` : '',
      step.agentRole ? `角色=${step.agentRole}` : '',
      step.agentId ? `ID=${step.agentId}` : '',
    ].filter(Boolean)
    parts.push(`   执行 Agent：${agentParts.join('，')}`)
  }
  if (step.modelName || step.modelConfigId) {
    const modelParts = [
      step.modelName ? `模型=${step.modelName}` : '',
      step.modelConfigId ? `配置ID=${step.modelConfigId}` : '',
    ].filter(Boolean)
    parts.push(`   模型配置：${modelParts.join('，')}`)
  }
  return parts.join('\n')
}

function buildPlanKey(plan: PlanInfo): string {
  return [
    plan.title,
    plan.steps.map((step) => `${step.id}:${step.title}`).join('|'),
  ].join('::')
}
</script>

<style scoped>
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  background: var(--bg-main);
}

.chat-panel.landing-mode {
  justify-content: center;
  padding: 7vh 24px 4vh;
}

.landing-shell {
  width: min(100%, 880px);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  transform: translateY(44px);
}

.greeting {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-bottom: 44px;
  color: var(--ink);
}

.greeting h1 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(2.75rem, 5vw, 4.25rem);
  font-weight: 500;
  line-height: 1;
  letter-spacing: -0.055em;
}

.greeting-mark {
  color: var(--accent);
  font-size: clamp(2rem, 3vw, 3rem);
  line-height: 1;
}

.onboarding-cta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-lg);
  max-width: 520px;
}

.cta-desc {
  margin: 0;
  text-align: center;
  color: var(--text-secondary);
  font-size: var(--font-size-base);
  line-height: 1.6;
}

.cta-btn {
  font-weight: 600;
}

.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 0 10px;
}

@media (max-width: 760px) {
  .chat-panel.landing-mode {
    padding: 5vh 16px 2vh;
  }

  .greeting {
    gap: 12px;
    margin-bottom: 30px;
  }

  .landing-shell {
    transform: translateY(8px);
  }
}
</style>
