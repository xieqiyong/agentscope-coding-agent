import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { chatApi } from '@/api/chat'
import type { Session, ChatMessage, ToolCallInfo, Confirmation, PatchFile, PlanInfo, PlanStep, ThinkingInfo, MessageUsage } from '@/types'
import type { RuntimeEvent, RuntimeEventType } from '@/types/events'

const STORAGE_CONVERSATION_ID = 'coding-agent-current-conversation-id'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<Session[]>([])
  const currentSession = ref<Session | null>(null)
  const messages = ref<ChatMessage[]>([])
  const isStreaming = ref(false)
  const streamingText = ref('')
  const pendingConfirmations = ref<Confirmation[]>([])
  // 后端返回的 conversationId，续聊时需要传回。
  const lastConversationId = ref<number | null>(restoreConversationId())
  // 当前正在运行的 runId，用于停止生成时通知后端取消执行。
  const activeRunId = ref<number | null>(null)

  const currentMessages = computed(() => messages.value)

  // 会话累计用量：汇总所有助手消息的 token 与成本，供聊天框底部常驻展示
  const sessionUsage = computed(() => {
    const total = { inputTokens: 0, outputTokens: 0, cachedTokens: 0, costUsd: 0 }
    for (const msg of messages.value) {
      if (msg.role !== 'assistant' || !msg.usage) continue
      total.inputTokens += msg.usage.inputTokens || 0
      total.outputTokens += msg.usage.outputTokens || 0
      total.cachedTokens += msg.usage.cachedTokens || 0
      total.costUsd += msg.usage.costUsd || 0
    }
    return total
  })
  const hasPendingConfirmation = computed(() => pendingConfirmations.value.length > 0)

  async function fetchSessions(workspaceId: string) {
    try {
      const res: any = await chatApi.listSessions(workspaceId)
      sessions.value = res.data || []
    } catch {
      sessions.value = []
    }
  }

  async function createSession(workspaceId: string, title?: string): Promise<Session | null> {
    try {
      const res: any = await chatApi.createSession({ workspaceId, title })
      const session = res.data
      sessions.value.unshift(session)
      return session
    } catch {
      return null
    }
  }

  async function selectSession(sessionId: string) {
    try {
      setActiveConversationId(sessionId)
      const res: any = await chatApi.getSession(sessionId)
      currentSession.value = res.data || null
      await loadMessages(sessionId)
    } catch {
      currentSession.value = null
      messages.value = []
    }
  }

  async function loadMessages(sessionId: string) {
    try {
      const res: any = await chatApi.getTimeline(sessionId)
      const rows = Array.isArray(res.data) ? res.data : []
      messages.value = rows
        .map(normalizeBackendMessage)
        .filter((msg: ChatMessage | null): msg is ChatMessage => Boolean(msg))
    } catch {
      try {
        const res: any = await chatApi.listMessages(sessionId)
        const rows = Array.isArray(res.data) ? res.data : []
        messages.value = rows
          .map(normalizeBackendMessage)
          .filter((msg: ChatMessage | null): msg is ChatMessage => Boolean(msg))
      } catch {
        messages.value = []
      }
    }
  }

  async function deleteSession(id: string) {
    await chatApi.deleteSession(id)
    sessions.value = sessions.value.filter((s) => String(s.id) !== String(id))
    // 如果删除的是当前会话，清空上下文。
    if (currentSession.value && String(currentSession.value.id) === String(id)) {
      clearSession()
    }
    // 如果删除的是当前续聊的会话，也清除 lastConversationId。
    if (lastConversationId.value && String(lastConversationId.value) === String(id)) {
      clearSession()
    }
  }

  // ==================== 后端 RuntimeEvent 处理 ====================

  /**
   * 处理后端 RuntimeEvent。
   *
   * 后端事件格式：
   *   type: RuntimeEventType 枚举名（如 ANSWER_DELTA, TOOL_CALL_STARTED）
   *   stage: 中文阶段名（如 "回答中", "工具调用"）
   *   content: 事件内容（delta 文本、工具结果等）
   *   metadata: 附加数据（toolName, callId, args 等）
   *   elapsedMs: 距运行开始的毫秒数
   */
  function handleRuntimeEvent(type: RuntimeEventType, event: RuntimeEvent) {
    switch (type) {
      case 'RUN_STARTED':
        rememberConversationFromEvent(event)
        rememberActiveRun(event)
        isStreaming.value = true
        streamingText.value = ''
        break

      case 'AGENT_STARTED': {
        const nodeId = eventTaskNodeId(event)
        if (nodeId) {
          const step = findPlanStep(nodeId)
          if (step) step.activity = '执行中'
          break
        }
        isStreaming.value = true
        break
      }

      case 'ANSWER_DELTA':
        handleAnswerDelta(event)
        break

      case 'ANSWER_FINISHED':
        // 不在这里 finalize！Agent 一次运行可能有多轮 think→tool→think，
        // ANSWER_FINISHED 只表示一轮文本输出结束，不代表整个回答结束。
        // 等到 RUN_FINISHED / AGENT_FINISHED 再统一 finalize。
        break

      case 'MODEL_CALL_STARTED':
        handleModelThinkingStarted(event)
        break

      case 'MODEL_CALL_FINISHED':
        handleModelThinkingFinished(event)
        break

      case 'THINKING_STARTED':
        handleThinkingStarted(event)
        break

      case 'THINKING_DELTA':
        handleThinkingDelta(event)
        break

      case 'THINKING_FINISHED':
        handleThinkingFinished(event)
        break

      case 'TOOL_CALL_STARTED':
        handleToolCallStarted(event)
        break

      case 'TOOL_CALL_ARGS_DELTA':
        handleToolCallArgsDelta(event)
        break

      case 'TOOL_RESULT_STARTED':
        handleToolResultStarted(event)
        break

      case 'TOOL_RESULT_DELTA':
      case 'TOOL_RESULT_DATA_DELTA':
        handleToolResultDelta(event)
        break

      case 'TOOL_RESULT_FINISHED':
        handleToolResultFinished(event)
        break

      case 'PLAN_CREATED':
        handlePlanCreated(event)
        break

      case 'PLAN_STEP_STATUS_CHANGED':
        handlePlanStepStatusChanged(event)
        break

      case 'TASK_GRAPH_STARTED':
        handleTaskGraphStarted(event)
        break

      case 'TASK_GRAPH_FINISHED':
        handleTaskGraphFinished(event)
        break

      case 'CONFIRMATION_REQUIRED':
        handleConfirmationRequired(event)
        break

      case 'RUN_STATUS_CHANGED':
        handleRunStatusChanged(event)
        break

      case 'AGENT_FINISHED': {
        const nodeId = eventTaskNodeId(event)
        if (nodeId) {
          const step = findPlanStep(nodeId)
          if (step && step.status === 'in_progress') step.activity = '正在收尾'
          break
        }
        finalizeStreamingMessage()
        break
      }

      case 'RUN_FINISHED':
        rememberConversationFromEvent(event)
        attachRunCost(event)
        if (readString(event.metadata?.status).toUpperCase() === 'CANCELLED') {
          markRunningPlanCancelled()
        }
        finalizeStreamingMessage()
        // 兜底：扫描所有 toolCall 和回答文本，检测遗漏的 patch 提案
        detectMissedPatchConfirmations()
        isStreaming.value = false
        activeRunId.value = null
        break

      case 'RUN_ERROR':
        markRunningPlanFailed()
        finalizeStreamingMessage()
        isStreaming.value = false
        activeRunId.value = null
        if (event.content) {
          messages.value.push({
            id: `error-${Date.now()}`,
            sessionId: currentSession.value?.id || '',
            role: 'assistant',
            content: `⚠️ **运行出错**：${event.content}`,
            timestamp: new Date().toISOString(),
          })
        }
        break

      case 'RUNTIME_WARNING':
        if (eventTaskNodeId(event)) {
          const step = findPlanStep(eventTaskNodeId(event))
          if (step) step.activity = event.content || '节点警告'
          break
        }
        if (event.content) {
          messages.value.push({
            id: `warn-${Date.now()}`,
            sessionId: currentSession.value?.id || '',
            role: 'assistant',
            content: `⚠️ ${event.content}`,
            timestamp: new Date().toISOString(),
          })
        }
        break
    }
  }

  function handleAnswerDelta(event: RuntimeEvent) {
    const delta = event.content || ''
    const nodeId = eventTaskNodeId(event)
    if (nodeId) {
      const step = findPlanStep(nodeId)
      if (step) {
        step.output = `${step.output || ''}${delta}`
        step.activity = '正在生成结果'
      }
      return
    }
    streamingText.value += delta

    const lastMsg = messages.value[messages.value.length - 1]
    // 如果最后一条是 assistant 消息（同一轮 Agent 运行），追加到同一条。
    if (lastMsg && lastMsg.role === 'assistant' && isStreaming.value && !lastMsg.confirmation) {
      lastMsg.content = streamingText.value
      lastMsg.isStreaming = true
    } else {
      messages.value.push({
        id: `streaming-${Date.now()}`,
        sessionId: currentSession.value?.id || '',
        role: 'assistant',
        content: streamingText.value,
        timestamp: new Date().toISOString(),
        isStreaming: true,
      })
    }
  }

  function finalizeStreamingMessage() {
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg?.isStreaming) {
      lastMsg.isStreaming = false
      lastMsg.id = `msg-${Date.now()}`
    }
    // 兜底收尾：中断（取消）时 THINKING_FINISHED 事件不会到达，需主动把仍在思考/运行的状态收尾，
    // 否则 UI 会一直显示"正在思考"。正常运行结束时这些已是 done/completed，这里只是保险。
    if (lastMsg?.thinking && lastMsg.thinking.status === 'thinking') {
      lastMsg.thinking.status = 'done'
    }
    if (lastMsg?.toolCalls) {
      for (const tc of lastMsg.toolCalls) {
        if (tc.status === 'running') tc.status = 'completed'
      }
    }
    streamingText.value = ''
  }

  function handleModelThinkingStarted(event: RuntimeEvent) {
    const nodeId = eventTaskNodeId(event)
    if (nodeId) {
      const step = findPlanStep(nodeId)
      if (step) step.activity = '思考中'
      return
    }
    isStreaming.value = true
    const message = ensureAssistantRuntimeMessage()
    if (!message.thinking || message.thinking.status === 'done') {
      message.thinking = {
        status: 'thinking',
        content: '',
        omitted: true,
        chars: 0,
        startedAt: Date.now(),
        durationMs: 0,
      }
    }
  }

  function handleModelThinkingFinished(event: RuntimeEvent) {
    // 累计模型调用的 token 用量（含缓存命中），无论是主回答还是多 Agent 节点
    accumulateUsage(event)
    const nodeId = eventTaskNodeId(event)
    if (nodeId) {
      const step = findPlanStep(nodeId)
      if (step && step.status === 'in_progress') step.activity = '执行中'
      return
    }
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg?.role === 'assistant' && lastMsg.thinking?.status === 'thinking') {
      handleThinkingFinished(event)
    }
  }

  // 把模型调用返回的 token 用量累加到最后一条助手消息上
  function accumulateUsage(event: RuntimeEvent) {
    const meta = event.metadata || {}
    const input = Number(meta.inputTokens) || 0
    const output = Number(meta.outputTokens) || 0
    if (!input && !output) return
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg?.role !== 'assistant') return
    const usage = lastMsg.usage || { inputTokens: 0, outputTokens: 0, cachedTokens: 0 }
    usage.inputTokens += input
    usage.outputTokens += output
    usage.cachedTokens += Number(meta.cachedTokens) || 0
    lastMsg.usage = usage
  }

  // RUN_FINISHED 携带的成本估算写回当前消息
  function attachRunCost(event: RuntimeEvent) {
    const cost = Number(event.metadata?.costUsd)
    if (!cost) return
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg?.role !== 'assistant') return
    const usage = lastMsg.usage || { inputTokens: 0, outputTokens: 0, cachedTokens: 0 }
    usage.costUsd = cost
    lastMsg.usage = usage
  }

  function handleThinkingStarted(event: RuntimeEvent) {
    const nodeId = eventTaskNodeId(event)
    if (nodeId) {
      const step = findPlanStep(nodeId)
      if (step) step.activity = '思考中'
      return
    }
    isStreaming.value = true
    const message = ensureAssistantRuntimeMessage()
    message.isStreaming = true
    message.thinking = {
      status: 'thinking',
      content: '',
      omitted: true,
      chars: 0,
      startedAt: Date.now(),
      durationMs: 0,
    }
    if (event.content) {
      message.thinking.content = event.content
      message.thinking.omitted = false
    }
  }

  function handleThinkingDelta(event: RuntimeEvent) {
    const nodeId = eventTaskNodeId(event)
    if (nodeId) {
      const step = findPlanStep(nodeId)
      if (step) step.activity = '思考中'
      return
    }
    const message = ensureAssistantRuntimeMessage()
    const thinking = ensureThinkingInfo(message)
    const meta = event.metadata || {}
    const delta = event.content || ''
    const chars = readNumber(meta.chars)

    thinking.status = 'thinking'
    thinking.omitted = meta.omitted === true
    if (delta) {
      thinking.content = `${thinking.content || ''}${delta}`
      thinking.omitted = false
    }
    if (chars > 0) {
      thinking.chars = (thinking.chars || 0) + chars
    }
  }

  function handleThinkingFinished(event: RuntimeEvent) {
    const nodeId = eventTaskNodeId(event)
    if (nodeId) {
      const step = findPlanStep(nodeId)
      if (step && step.status === 'in_progress') step.activity = '执行中'
      return
    }
    const message = ensureAssistantRuntimeMessage()
    const thinking = ensureThinkingInfo(message)
    thinking.status = 'done'
    if (thinking.startedAt) {
      thinking.durationMs = Date.now() - thinking.startedAt
    }
  }

  function ensureThinkingInfo(message: ChatMessage) {
    if (!message.thinking) {
      message.thinking = {
        status: 'thinking',
        content: '',
        omitted: true,
        chars: 0,
        startedAt: Date.now(),
        durationMs: 0,
      }
    }
    return message.thinking
  }

  function handleToolCallStarted(event: RuntimeEvent) {
    const meta = event.metadata || {}
    const callId = eventCallId(event, event.eventId)
    const toolName = readString(meta.toolName) || readString(meta.tool) || event.stage || '未知工具'
    const taskNodeId = eventTaskNodeId(event)
    const nodeStep = taskNodeId ? findPlanStep(taskNodeId) : undefined
    if (nodeStep) nodeStep.activity = `调用 ${toolName}`
    const lastMsg = ensureAssistantToolMessage()
    const existing = findToolCall(callId)
    if (existing) {
      existing.toolName = toolName
      existing.status = 'running'
      return
    }

    if (!lastMsg.toolCalls) lastMsg.toolCalls = []
    lastMsg.toolCalls.push({
      callId,
      toolName,
      args: readArgs(meta.args),
      argsText: '',
      status: 'running',
      startedAt: Date.now(),
      taskNodeId: taskNodeId || undefined,
      agentName: readString(meta.agentName) || nodeStep?.agentName,
    })
  }

  function handleToolCallArgsDelta(event: RuntimeEvent) {
    const meta = event.metadata || {}
    const callId = eventCallId(event)
    const toolCall = findToolCall(callId)
    if (!toolCall) return

    toolCall.argsText = `${toolCall.argsText || ''}${event.content || ''}`
    toolCall.args = parseToolArgs(toolCall.argsText)
  }

  function handleToolResultStarted(event: RuntimeEvent) {
    const meta = event.metadata || {}
    const callId = eventCallId(event, event.eventId)
    const toolName = readString(meta.toolName) || readString(meta.tool) || event.stage || '未知工具'
    const taskNodeId = eventTaskNodeId(event)
    let toolCall = findToolCall(callId)

    // 兜底：callId 不匹配时按工具名找
    if (!toolCall) {
      toolCall = findRunningToolCallByName(toolName, taskNodeId)
    }
    if (!toolCall) {
      const lastMsg = ensureAssistantToolMessage()
      if (!lastMsg.toolCalls) lastMsg.toolCalls = []
      toolCall = {
        callId,
        toolName: readString(meta.toolName) || readString(meta.tool) || event.stage || '未知工具',
        args: {},
        argsText: '',
        result: '',
        status: 'running',
        startedAt: Date.now(),
        taskNodeId: taskNodeId || undefined,
        agentName: readString(meta.agentName),
      }
      lastMsg.toolCalls.push(toolCall)
    }
    toolCall.status = 'running'
  }

  function handleToolResultDelta(event: RuntimeEvent) {
    const meta = event.metadata || {}
    const callId = eventCallId(event)
    const taskNodeId = eventTaskNodeId(event)
    let toolCall = findToolCall(callId)

    // 兜底：如果 callId 不匹配，尝试找最近消息中正在运行的 toolCall
    if (!toolCall) {
      const toolName = readString(meta.toolName) || readString(meta.tool)
      toolCall = findRunningToolCallByName(toolName, taskNodeId)
    }
    if (!toolCall) return

    toolCall.result = `${toolCall.result || ''}${event.content || ''}`
  }

  function handleToolResultFinished(event: RuntimeEvent) {
    const meta = event.metadata || {}
    const callId = eventCallId(event)
    const taskNodeId = eventTaskNodeId(event)
    let toolCall = findToolCall(callId)

    // 兜底：callId 不匹配时按工具名找最近完成的 toolCall
    if (!toolCall) {
      const toolName = readString(meta.toolName) || readString(meta.tool)
      toolCall = findRunningToolCallByName(toolName, taskNodeId)
    }

    if (!toolCall) {
      console.warn('[ChatStore] TOOL_RESULT_FINISHED: 未找到 toolCall, callId=', callId, 'event=', event)
      return
    }

    const state = readString(meta.state).toUpperCase()
    toolCall.status = ['ERROR', 'FAILED', 'TIMEOUT', 'REJECTED'].includes(state) ? 'error' : 'completed'
    toolCall.durationMs = event.elapsedMs || (Date.now() - (toolCall.startedAt || Date.now()))
    if (taskNodeId) {
      const step = findPlanStep(taskNodeId)
      if (step && step.status === 'in_progress') step.activity = '执行中'
    }

    if (isPatchProposalTool(toolCall.toolName)) {
      console.log('[ChatStore] 检测到 patch 提案工具:', toolCall.toolName, 'result=', toolCall.result)
      void registerPatchConfirmation(toolCall)
    }
  }

  function handlePlanCreated(event: RuntimeEvent) {
    rememberConversationFromEvent(event)
    const plan = normalizePlanInfo(event.metadata?.plan)
    if (!plan) return

    isStreaming.value = true
    const message = ensureAssistantRuntimeMessage()
    message.plan = plan
    message.content = message.content || ''
    message.isStreaming = true
  }

  function handlePlanStepStatusChanged(event: RuntimeEvent) {
    const stepId = readString(event.metadata?.stepId)
    const status = normalizePlanStepStatus(event.metadata?.status)
    if (!stepId || !status) return

    const message = findLatestPlanMessage()
    const step = message?.plan?.steps.find((item) => String(item.id) === stepId)
    if (step) {
      step.status = status
      step.agentId = readString(event.metadata?.agentId) || step.agentId
      step.agentName = readString(event.metadata?.agentName) || step.agentName
      step.agentRole = readString(event.metadata?.agentRole) || step.agentRole
      step.modelConfigId = readString(event.metadata?.modelConfigId) || step.modelConfigId
      step.modelName = readString(event.metadata?.modelName) || step.modelName
      step.dependsOn = readStringList(event.metadata?.dependsOn).length > 0
        ? readStringList(event.metadata?.dependsOn)
        : step.dependsOn
      step.attempt = readNumber(event.metadata?.attempt) || step.attempt
      const output = readString(event.metadata?.output)
      if (output || status === 'completed') step.output = output
      step.errorMessage = readString(event.metadata?.errorMessage) || undefined
      step.startedAt = readString(event.metadata?.startedAt) || step.startedAt
      step.finishedAt = readString(event.metadata?.finishedAt) || step.finishedAt
      step.activity = status === 'in_progress'
        ? (event.content || '执行中')
        : status === 'waiting'
          ? '等待继续'
          : undefined
      if (message?.plan) {
        refreshPlanExecutionStatus(message.plan)
      }
    }
  }

  function handleTaskGraphStarted(event: RuntimeEvent) {
    isStreaming.value = true
    const message = findLatestPlanMessage()
    if (message?.plan) {
      message.plan.executionStatus = 'running'
      message.plan.maxConcurrency = readNumber(event.metadata?.maxConcurrency)
        || message.plan.maxConcurrency
    }
  }

  function handleTaskGraphFinished(event: RuntimeEvent) {
    const message = findLatestPlanMessage()
    if (!message?.plan) return
    const status = readString(event.metadata?.status).toUpperCase()
    if (status === 'COMPLETED') message.plan.executionStatus = 'completed'
    else if (status === 'WAITING_APPROVAL') message.plan.executionStatus = 'waiting'
    else if (status === 'FAILED') message.plan.executionStatus = 'failed'
  }

  function refreshPlanExecutionStatus(plan: PlanInfo) {
    if (plan.steps.some((step) => step.status === 'in_progress')) {
      plan.executionStatus = 'running'
      return
    }
    if (plan.steps.some((step) => step.status === 'waiting')) {
      plan.executionStatus = 'waiting'
      return
    }
    if (plan.steps.length > 0 && plan.steps.every((step) => step.status === 'completed')) {
      plan.executionStatus = 'completed'
      return
    }
    if (plan.steps.some((step) => step.status === 'failed')) {
      plan.executionStatus = 'failed'
      return
    }
    if (plan.steps.some((step) => step.status === 'cancelled')) {
      plan.executionStatus = 'cancelled'
      return
    }
    plan.executionStatus = 'idle'
  }

  function ensureAssistantRuntimeMessage(): ChatMessage {
    let lastMsg = messages.value[messages.value.length - 1]
    // 如果最后一条是 assistant 消息（同一轮运行），复用它挂载运行期状态。
    if (!lastMsg || lastMsg.role !== 'assistant' || !isStreaming.value || lastMsg.confirmation) {
      lastMsg = {
        id: `runtime-${Date.now()}`,
        sessionId: currentSession.value?.id || '',
        role: 'assistant',
        content: '',
        timestamp: new Date().toISOString(),
        isStreaming: true,
      }
      messages.value.push(lastMsg)
    }
    return lastMsg
  }

  function handleRunStatusChanged(event: RuntimeEvent) {
    const status = readString(event.metadata?.status).toUpperCase()
    if (status === 'RUNNING') {
      isStreaming.value = true
    }
    if (status === 'CANCELLED') {
      markRunningPlanCancelled()
      finalizeStreamingMessage()
      isStreaming.value = false
      activeRunId.value = null
    }
    if (status === 'WAITING_APPROVAL') {
      isStreaming.value = false
      finalizeStreamingMessage()
    }
  }

  function ensureAssistantToolMessage(): ChatMessage {
    const lastMsg = ensureAssistantRuntimeMessage()
    if (!lastMsg.toolCalls) lastMsg.toolCalls = []
    return lastMsg
  }

  function findToolCall(callId?: string): ToolCallInfo | null {
    if (!callId) return null
    for (const msg of messages.value) {
      if (!msg.toolCalls) continue
      const found = msg.toolCalls.find((tc) => tc.callId === callId)
      if (found) return found
    }
    return null
  }

  /**
   * 兜底查找：当 callId 不匹配时，按工具名找最近一个正在运行的 toolCall。
   * 解决 AgentScope 不传 toolCallId 时每个事件 eventId 不同导致匹配失败的问题。
   */
  function findRunningToolCallByName(toolName?: string, taskNodeId?: string): ToolCallInfo | null {
    if (!toolName) return null
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const msg = messages.value[i]
      if (!msg.toolCalls) continue
      for (let j = msg.toolCalls.length - 1; j >= 0; j--) {
        const tc = msg.toolCalls[j]
        if (tc.status === 'running' && tc.toolName === toolName
          && (!taskNodeId || tc.taskNodeId === taskNodeId)) return tc
      }
    }
    return null
  }

  function readString(value: unknown): string {
    if (typeof value === 'string') return value
    if (typeof value === 'number' && Number.isFinite(value)) return String(value)
    return ''
  }

  function eventTaskNodeId(event: RuntimeEvent): string {
    return readString(event.metadata?.taskNodeId) || readString(event.metadata?.stepId)
  }

  function eventCallId(event: RuntimeEvent, fallback = ''): string {
    const rawCallId = readString(event.metadata?.callId)
      || readString(event.metadata?.toolCallId)
      || fallback
    const nodeId = eventTaskNodeId(event)
    return nodeId && rawCallId ? `${nodeId}:${rawCallId}` : rawCallId
  }

  function findPlanStep(nodeId: string): PlanStep | undefined {
    return findLatestPlanMessage()?.plan?.steps.find((step) => String(step.id) === nodeId)
  }

  function readNumber(value: unknown): number {
    return typeof value === 'number' && Number.isFinite(value) ? value : 0
  }

  function readArgs(value: unknown): Record<string, unknown> {
    return value && typeof value === 'object' && !Array.isArray(value)
      ? value as Record<string, unknown>
      : {}
  }

  function normalizePlanInfo(value: unknown): PlanInfo | undefined {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined
    const raw = value as Record<string, unknown>
    const steps = Array.isArray(raw.steps)
      ? raw.steps.map(normalizePlanStep)
      : []

    return {
      graphVersion: readNumber(raw.graphVersion) || 1,
      maxConcurrency: readNumber(raw.maxConcurrency) || 1,
      title: readString(raw.title) || '执行计划',
      summary: readString(raw.summary),
      riskLevel: readRiskLevel(raw.riskLevel),
      steps,
      acceptanceCriteria: readStringList(raw.acceptanceCriteria),
      expectedTools: readStringList(raw.expectedTools),
      requiresApproval: raw.requiresApproval === true,
      executionStatus: normalizePlanExecutionStatus(raw.executionStatus),
    }
  }

  function normalizePlanStep(value: unknown, index: number): PlanStep {
    const raw = value && typeof value === 'object' && !Array.isArray(value)
      ? value as Record<string, unknown>
      : {}
    return {
      id: readString(raw.id) || String(index + 1),
      title: readString(raw.title) || `步骤 ${index + 1}`,
      description: readString(raw.description),
      status: normalizePlanStepStatus(raw.status) || 'pending',
      agentId: readString(raw.agentId) || undefined,
      agentName: readString(raw.agentName) || 'ExecutorAgent',
      agentRole: readString(raw.agentRole) || undefined,
      modelConfigId: readString(raw.modelConfigId) || undefined,
      modelName: readString(raw.modelName) || readString(raw.model),
      tools: readStringList(raw.tools),
      dependsOn: readStringList(raw.dependsOn),
      attempt: readNumber(raw.attempt) || undefined,
      output: readString(raw.output) || undefined,
      errorMessage: readString(raw.errorMessage) || undefined,
      startedAt: readString(raw.startedAt) || undefined,
      finishedAt: readString(raw.finishedAt) || undefined,
    }
  }

  function normalizePlanStepStatus(value: unknown): PlanStep['status'] | '' {
    const text = readString(value).toLowerCase()
    if (text === 'pending' || text === 'ready' || text === 'in_progress' || text === 'completed'
      || text === 'failed' || text === 'waiting' || text === 'cancelled') {
      return text
    }
    return ''
  }

  function normalizePlanExecutionStatus(value: unknown): PlanInfo['executionStatus'] {
    const text = readString(value).toLowerCase()
    if (text === 'idle' || text === 'running' || text === 'waiting' || text === 'completed'
      || text === 'failed' || text === 'cancelled') {
      return text
    }
    return undefined
  }

  function readStringList(value: unknown): string[] {
    if (!Array.isArray(value)) return []
    return value.map(readString).filter(Boolean)
  }

  function findLatestPlanMessage(): ChatMessage | null {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const msg = messages.value[i]
      if (msg.role === 'assistant' && msg.plan) return msg
    }
    return null
  }

  function isPatchProposalTool(toolName: string): boolean {
    return toolName === 'propose_patch' || toolName === 'propose_file_change'
  }

  function parseToolArgs(text?: string): Record<string, unknown> {
    if (!text?.trim()) return {}
    try {
      const parsed = JSON.parse(text)
      return readArgs(parsed)
    } catch {
      return { _raw: text }
    }
  }

  function handleConfirmationRequired(event: RuntimeEvent) {
    const meta = event.metadata || {}
    if (readString(meta.requestType) === 'TOOL_PERMISSION') {
      const approvals = Array.isArray(meta.approvalRequests) ? meta.approvalRequests : []
      const firstApproval = approvals[0] && typeof approvals[0] === 'object'
        ? approvals[0] as Record<string, unknown>
        : {}
      const toolCalls = Array.isArray(meta.toolCalls) ? meta.toolCalls : []
      const firstToolCall = toolCalls[0] && typeof toolCalls[0] === 'object'
        ? toolCalls[0] as Record<string, unknown>
        : {}
      const approvalId = readString(meta.approvalId) || readString(firstApproval.approvalId)
      const toolName = readString(firstApproval.toolName) || readString(firstToolCall.name) || '未知工具'
      const toolCallId = readString(firstApproval.toolCallId) || readString(firstToolCall.id)
      const confirmation: Confirmation = {
        patchId: `tool-approval-${approvalId || event.eventId}`,
        kind: 'TOOL_PERMISSION',
        approvalId,
        runId: event.runId,
        toolName,
        toolCallId,
        files: [],
        diff: '',
        riskLevel: readRiskLevel(meta.riskLevel || firstApproval.riskLevel || firstToolCall.riskLevel),
        summary: `Agent 请求执行工具 ${toolName}，需要你确认后继续。`,
      }
      addConfirmationMessage(confirmation)
      return
    }

    const confirmation: Confirmation = {
      patchId: readString(meta.patchId) || event.eventId,
      kind: 'PATCH',
      files: readPatchFiles(meta.files),
      diff: readString(meta.diff) || event.content || '',
      riskLevel: readRiskLevel(meta.riskLevel),
      summary: event.content || '智能体提议修改代码',
    }
    addConfirmationMessage(confirmation)
  }

  async function registerPatchConfirmation(toolCall: ToolCallInfo) {
    const resultText = toolCall.result || toolCall.argsText || ''
    const patchId = extractPatchId(resultText)
    console.log('[ChatStore] registerPatchConfirmation: resultText=', resultText, 'patchId=', patchId)
    if (!patchId || hasConfirmation(patchId)) return
    toolCall.patchId = patchId

    try {
      const { patchApi } = await import('@/api/patch')
      const res: any = await patchApi.get(patchId)
      const patch = res.data || {}
      const diff = patch.diff || patch.diffText || ''
      console.log('[ChatStore] patch API 返回: id=', patch.id, 'diff长度=', diff.length, 'files=', patch.files)
      const confirmation: Confirmation = {
        patchId: String(patch.id || patchId),
        files: normalizePatchFiles(patch.files, diff),
        diff,
        riskLevel: 'MEDIUM',
        summary: patch.summary || patch.title || '智能体生成了代码修改提案',
      }
      addConfirmationMessage(confirmation)
    } catch (e) {
      console.warn('[ChatStore] patch API 调用失败:', e)
      // API 失败时，用工具结果中能提取的信息创建确认卡片
      const confirmation: Confirmation = {
        patchId,
        files: [],
        diff: '',
        riskLevel: 'MEDIUM',
        summary: '智能体生成了代码修改提案，但暂时无法加载 diff 详情',
      }
      addConfirmationMessage(confirmation)
    }
  }

  function addConfirmationMessage(confirmation: Confirmation) {
    if (hasConfirmation(confirmation.patchId)) return
    pendingConfirmations.value.push(confirmation)
    messages.value.push({
      id: `confirm-${confirmation.patchId}-${Date.now()}`,
      sessionId: currentSession.value?.id || '',
      role: 'assistant',
      content: `**提议的修改**：${confirmation.summary}`,
      confirmation,
      timestamp: new Date().toISOString(),
    })
  }

  function hasConfirmation(patchId: string): boolean {
    return pendingConfirmations.value.some((item) => String(item.patchId) === String(patchId))
      || messages.value.some((item) => String(item.confirmation?.patchId || '') === String(patchId))
  }

  function extractPatchId(text: string): string | null {
    if (!text) return null
    // 匹配多种格式：
    // "补丁 ID：123" / "补丁ID: 123" / "patchId: 123" / "patch_id: 123"
    // "ID: 123" / "id=123" / "提案编号：123"
    const patterns = [
      /(?:补丁\s*ID|patch_?id)\s*[:：=]\s*(\d+)/i,
      /(?:提案|patch)\s*[:：#]\s*(\d+)/i,
      /\bID\s*[:：]\s*(\d+)/i,
      /\bid\s*=\s*(\d+)/i,
    ]
    for (const pattern of patterns) {
      const matched = text.match(pattern)
      if (matched?.[1]) return matched[1]
    }
    return null
  }

  function normalizePatchFiles(files: unknown, diff: string): PatchFile[] {
    if (Array.isArray(files) && files.length > 0) {
      return files.map((file: any) => ({
        path: String(file.path || file.filePath || ''),
        changeType: normalizeChangeType(file.changeType),
        additions: Number(file.additions || 0),
        deletions: Number(file.deletions || 0),
      })).filter((file) => file.path)
    }
    return extractPatchFilesFromDiff(diff)
  }

  function readPatchFiles(value: unknown): PatchFile[] {
    return Array.isArray(value) ? normalizePatchFiles(value, '') : []
  }

  function readRiskLevel(value: unknown): Confirmation['riskLevel'] {
    return value === 'LOW' || value === 'MEDIUM' || value === 'HIGH' || value === 'CRITICAL'
      ? value
      : 'MEDIUM'
  }

  function normalizeChangeType(value: unknown): PatchFile['changeType'] {
    const text = String(value || '').toUpperCase()
    if (text === 'ADD' || text === 'ADDED' || text === 'CREATE') return 'added'
    if (text === 'DELETE' || text === 'DELETED' || text === 'REMOVE') return 'deleted'
    return 'modified'
  }

  function extractPatchFilesFromDiff(diff: string): PatchFile[] {
    if (!diff.trim()) return []
    const stats = new Map<string, PatchFile>()
    let currentPath = ''
    for (const line of diff.split('\n')) {
      if (line.startsWith('+++ b/')) {
        currentPath = line.slice(6).trim()
        if (currentPath && !stats.has(currentPath)) {
          stats.set(currentPath, { path: currentPath, changeType: 'modified', additions: 0, deletions: 0 })
        }
        continue
      }
      if (line.startsWith('--- a/') && !currentPath) {
        const path = line.slice(6).trim()
        if (path && !stats.has(path)) {
          stats.set(path, { path, changeType: 'modified', additions: 0, deletions: 0 })
        }
        currentPath = path
        continue
      }
      if (!currentPath || !stats.has(currentPath)) continue
      const file = stats.get(currentPath)!
      if (line.startsWith('+') && !line.startsWith('+++')) file.additions += 1
      if (line.startsWith('-') && !line.startsWith('---')) file.deletions += 1
    }
    return Array.from(stats.values())
  }

  async function applyPatch(patchId: string): Promise<boolean> {
    try {
      const { patchApi } = await import('@/api/patch')
      await patchApi.apply(patchId)
      pendingConfirmations.value = pendingConfirmations.value.filter(
        (c) => c.patchId !== patchId,
      )
      return true
    } catch {
      return false
    }
  }

  function rejectPatch(patchId: string) {
    pendingConfirmations.value = pendingConfirmations.value.filter(
      (c) => c.patchId !== patchId,
    )
  }

  function resolveConfirmation(patchId: string) {
    pendingConfirmations.value = pendingConfirmations.value.filter(
      (c) => c.patchId !== patchId,
    )
  }

  /**
   * 兜底检测：扫描本次运行中所有 toolCall 和回答文本，
   * 如果发现有 patch 提案但还没创建确认卡片，就自动补上。
   * 防止因 callId 不匹配或事件丢失导致确认卡片不弹出。
   */
  function detectMissedPatchConfirmations() {
    for (const msg of messages.value) {
      if (msg.role !== 'assistant' || !msg.toolCalls?.length) continue
      for (const tc of msg.toolCalls) {
        if (!isPatchProposalTool(tc.toolName)) continue
        // 已经有 patchId 关联的跳过
        if (tc.patchId && hasConfirmation(tc.patchId)) continue

        // 从 toolCall.result 或 toolCall.args 中提取 patchId
        const patchId = extractPatchId(tc.result || tc.argsText || '')
        if (!patchId) continue

        console.log('[ChatStore] 兜底检测到遗漏的 patch:', tc.toolName, 'patchId=', patchId)
        tc.patchId = patchId
        void registerPatchConfirmation(tc)
      }
    }

    // 如果 toolCall 里没找到，再扫描回答文本本身
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg?.role === 'assistant' && lastMsg.content) {
      const patchId = extractPatchId(lastMsg.content)
      if (patchId && !hasConfirmation(patchId)) {
        console.log('[ChatStore] 兜底从回答文本检测到 patchId=', patchId)
        // 创建一个简单的确认卡片
        const confirmation: Confirmation = {
          patchId,
          files: [],
          diff: '',
          riskLevel: 'MEDIUM',
          summary: '智能体生成了代码修改提案',
        }
        addConfirmationMessage(confirmation)
      }
    }
  }

  function markRunningPlanFailed() {
    const message = findLatestPlanMessage()
    if (!message?.plan || message.plan.executionStatus !== 'running') return
    for (const step of message.plan.steps) {
      if (step.status === 'in_progress' || step.status === 'ready') {
        step.status = 'failed'
        step.activity = undefined
      }
    }
    refreshPlanExecutionStatus(message.plan)
  }

  function markRunningPlanCancelled() {
    const message = findLatestPlanMessage()
    if (!message?.plan || message.plan.executionStatus !== 'running') return
    for (const step of message.plan.steps) {
      if (step.status === 'in_progress' || step.status === 'ready') {
        step.status = 'cancelled'
        step.activity = undefined
      }
    }
    message.plan.executionStatus = 'cancelled'
  }

  function cancelActiveRunLocally() {
    markRunningPlanCancelled()
    finalizeStreamingMessage()
    isStreaming.value = false
    activeRunId.value = null
  }

  function addUserMessage(content: string, options?: { messageKind?: 'plan-execute' }) {
    messages.value.push({
      id: `user-${Date.now()}`,
      sessionId: currentSession.value?.id || '',
      role: 'user',
      content,
      messageKind: options?.messageKind,
      timestamp: new Date().toISOString(),
    })
  }

  function clearSession() {
    currentSession.value = null
    messages.value = []
    streamingText.value = ''
    pendingConfirmations.value = []
    lastConversationId.value = null
    activeRunId.value = null
    localStorage.removeItem(STORAGE_CONVERSATION_ID)
  }

  function setActiveConversationId(value: string | number | null | undefined) {
    if (value == null || value === '') return
    const id = Number(value)
    if (!Number.isFinite(id)) return
    lastConversationId.value = id
    localStorage.setItem(STORAGE_CONVERSATION_ID, String(id))
  }

  function restoreConversationId(): number | null {
    const raw = localStorage.getItem(STORAGE_CONVERSATION_ID)
    if (!raw) return null
    const id = Number(raw)
    return Number.isFinite(id) ? id : null
  }

  function rememberConversationFromEvent(event: RuntimeEvent) {
    const id = event.metadata?.conversationId
    if (typeof id === 'number' || typeof id === 'string') {
      setActiveConversationId(id)
    }
  }

  function rememberActiveRun(event: RuntimeEvent) {
    if (typeof event.runId === 'number') {
      activeRunId.value = event.runId
    }
  }

  function normalizeBackendMessage(row: any): ChatMessage | null {
    const roleText = String(row?.role || '').toLowerCase()
    if (roleText !== 'user' && roleText !== 'assistant') return null
    const plan = normalizePlanInfo(row.plan)
    const content = String(row.content || '')
    return {
      id: String(row.id || `msg-${Date.now()}`),
      sessionId: String(row.conversationId || row.sessionId || ''),
      role: roleText,
      content,
      messageKind: inferMessageKind(roleText, content),
      timestamp: row.createdAt || row.updatedAt || new Date().toISOString(),
      toolCalls: normalizeBackendToolCalls(row.toolCalls),
      plan,
      // 刷新后从 timeline 重建的思考内容与 token 用量
      thinking: normalizeBackendThinking(row.thinking),
      usage: normalizeBackendUsage(row.usage),
    }
  }

  function normalizeBackendThinking(value: unknown): ThinkingInfo | undefined {
    if (!value || typeof value !== 'object') return undefined
    const raw = value as Record<string, unknown>
    const content = typeof raw.content === 'string' ? raw.content : ''
    if (!content) return undefined
    return {
      status: 'done',
      content,
      chars: typeof raw.chars === 'number' ? raw.chars : content.length,
      startedAt: typeof raw.startedAt === 'number' ? raw.startedAt : undefined,
      durationMs: typeof raw.durationMs === 'number' ? raw.durationMs : undefined,
    }
  }

  function normalizeBackendUsage(value: unknown): MessageUsage | undefined {
    if (!value || typeof value !== 'object') return undefined
    const raw = value as Record<string, unknown>
    const usage: MessageUsage = {
      inputTokens: Number(raw.inputTokens) || 0,
      outputTokens: Number(raw.outputTokens) || 0,
      cachedTokens: Number(raw.cachedTokens) || 0,
    }
    if (!usage.inputTokens && !usage.outputTokens) return undefined
    if (raw.costUsd != null && Number(raw.costUsd) > 0) usage.costUsd = Number(raw.costUsd)
    return usage
  }

  function inferMessageKind(role: 'user' | 'assistant', content: string): ChatMessage['messageKind'] | undefined {
    if (role === 'user' && content.startsWith('执行计划：') && content.includes('请作为 ExecutorAgent')) {
      return 'plan-execute'
    }
    return undefined
  }

  function normalizeBackendToolCalls(value: unknown): ToolCallInfo[] | undefined {
    if (!Array.isArray(value) || value.length === 0) return undefined
    return value.map((item: any) => ({
      callId: String(item.callId || item.toolCallId || `tool-${Date.now()}`),
      toolName: String(item.toolName || item.tool || 'unknown_tool'),
      args: readArgs(item.args),
      argsText: typeof item.argsText === 'string' ? item.argsText : '',
      result: typeof item.result === 'string' ? item.result : '',
      status: item.status === 'running' || item.status === 'error' ? item.status : 'completed',
      startedAt: typeof item.startedAt === 'number' ? item.startedAt : undefined,
      durationMs: typeof item.durationMs === 'number' ? item.durationMs : undefined,
      patchId: typeof item.patchId === 'string' ? item.patchId : undefined,
      taskNodeId: typeof item.taskNodeId === 'string' ? item.taskNodeId : undefined,
      agentName: typeof item.agentName === 'string' ? item.agentName : undefined,
    }))
  }

  return {
    sessions,
    currentSession,
    messages,
    isStreaming,
    streamingText,
    pendingConfirmations,
    lastConversationId,
    activeRunId,
    currentMessages,
    sessionUsage,
    hasPendingConfirmation,
    fetchSessions,
    createSession,
    selectSession,
    loadMessages,
    deleteSession,
    handleRuntimeEvent,
    finalizeStreamingMessage,
    addUserMessage,
    applyPatch,
    rejectPatch,
    resolveConfirmation,
    clearSession,
    setActiveConversationId,
    restoreConversationId,
    cancelActiveRunLocally,
  }
})
