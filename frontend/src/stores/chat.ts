import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { chatApi } from '@/api/chat'
import type { Session, ChatMessage, ToolCallInfo, Confirmation } from '@/types'
import type {
  AgentStreamEvent,
  AnswerDeltaPayload,
  ToolCallStartedPayload,
  ToolCallArgsDeltaPayload,
  ToolResultDeltaPayload,
  ConfirmationRequiredPayload,
} from '@/types/events'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<Session[]>([])
  const currentSession = ref<Session | null>(null)
  const messages = ref<ChatMessage[]>([])
  const isStreaming = ref(false)
  const streamingText = ref('')
  const pendingConfirmations = ref<Confirmation[]>([])

  const currentMessages = computed(() => messages.value)
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
      const res: any = await chatApi.listMessages(sessionId)
      messages.value = res.data || []
    } catch {
      messages.value = []
    }
  }

  async function deleteSession(id: string) {
    await chatApi.deleteSession(id)
    sessions.value = sessions.value.filter((s) => s.id !== id)
    if (currentSession.value?.id === id) {
      currentSession.value = null
      messages.value = []
    }
  }

  // ==================== SSE Event Handlers ====================

  function handleEvent(event: AgentStreamEvent) {
    switch (event.type) {
      case 'agent_started':
        isStreaming.value = true
        streamingText.value = ''
        break

      case 'agent_finished':
        finalizeStreamingMessage()
        isStreaming.value = false
        break

      case 'answer_delta':
        handleAnswerDelta(event.payload as AnswerDeltaPayload)
        break

      case 'tool_call_started':
        handleToolCallStarted(event.payload as ToolCallStartedPayload)
        break

      case 'tool_call_args_delta':
        handleToolCallArgsDelta(event.payload as ToolCallArgsDeltaPayload)
        break

      case 'tool_result_delta':
        handleToolResultDelta(event.payload as ToolResultDeltaPayload)
        break

      case 'confirmation_required':
        handleConfirmationRequired(event.payload as ConfirmationRequiredPayload)
        break
    }
  }

  function handleAnswerDelta(payload: AnswerDeltaPayload) {
    streamingText.value += payload.delta

    // Update or create the streaming assistant message
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg && lastMsg.isStreaming && lastMsg.role === 'assistant') {
      lastMsg.content = streamingText.value
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
    streamingText.value = ''
  }

  function handleToolCallStarted(payload: ToolCallStartedPayload) {
    // Attach tool call to the last assistant message or create one
    let lastMsg = messages.value[messages.value.length - 1]
    if (!lastMsg || lastMsg.role !== 'assistant') {
      lastMsg = {
        id: `tool-${Date.now()}`,
        sessionId: currentSession.value?.id || '',
        role: 'assistant',
        content: '',
        timestamp: new Date().toISOString(),
        toolCalls: [],
      }
      messages.value.push(lastMsg)
    }
    if (!lastMsg.toolCalls) lastMsg.toolCalls = []

    lastMsg.toolCalls.push({
      callId: payload.callId,
      toolName: payload.toolName,
      args: payload.args,
      status: 'running',
      startedAt: Date.now(),
    })
  }

  function handleToolCallArgsDelta(_payload: ToolCallArgsDeltaPayload) {
    // For now, args are already captured in tool_call_started
    // Future: incremental arg building
  }

  function handleToolResultDelta(payload: ToolResultDeltaPayload) {
    for (const msg of messages.value) {
      if (!msg.toolCalls) continue
      for (const tc of msg.toolCalls) {
        if (tc.callId === payload.callId) {
          tc.result = (tc.result || '') + payload.resultDelta
          tc.status = 'completed'
          tc.durationMs = Date.now() - (tc.startedAt || Date.now())
        }
      }
    }
  }

  function handleConfirmationRequired(payload: ConfirmationRequiredPayload) {
    const confirmation: Confirmation = {
      patchId: payload.patchId,
      files: payload.files,
      diff: payload.diff,
      riskLevel: payload.riskLevel as Confirmation['riskLevel'],
      summary: payload.summary,
    }
    pendingConfirmations.value.push(confirmation)

    // Add a confirmation card to the message stream
    messages.value.push({
      id: `confirm-${Date.now()}`,
      sessionId: currentSession.value?.id || '',
      role: 'assistant',
      content: `**提议的修改**：${payload.summary}\n\n${payload.files.length} 个文件受影响。风险等级：**${payload.riskLevel}**`,
      timestamp: new Date().toISOString(),
    })
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

  // User sends a message — adds to local list; SSE sending is handled by useSse composable
  function addUserMessage(content: string) {
    messages.value.push({
      id: `user-${Date.now()}`,
      sessionId: currentSession.value?.id || '',
      role: 'user',
      content,
      timestamp: new Date().toISOString(),
    })
  }

  function clearSession() {
    currentSession.value = null
    messages.value = []
    streamingText.value = ''
    pendingConfirmations.value = []
  }

  return {
    sessions,
    currentSession,
    messages,
    isStreaming,
    streamingText,
    pendingConfirmations,
    currentMessages,
    hasPendingConfirmation,
    fetchSessions,
    createSession,
    selectSession,
    loadMessages,
    deleteSession,
    handleEvent,
    addUserMessage,
    applyPatch,
    rejectPatch,
    clearSession,
  }
})
