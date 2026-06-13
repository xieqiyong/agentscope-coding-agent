import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { chatApi } from '@/api/chat'
import type { Session, ChatMessage, ToolCallInfo, Confirmation, PatchFile } from '@/types'
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
      case 'AGENT_STARTED':
        rememberConversationFromEvent(event)
        isStreaming.value = true
        streamingText.value = ''
        break

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

      case 'CONFIRMATION_REQUIRED':
        handleConfirmationRequired(event)
        break

      case 'RUN_FINISHED':
      case 'AGENT_FINISHED':
        rememberConversationFromEvent(event)
        finalizeStreamingMessage()
        // 兜底：扫描所有 toolCall 和回答文本，检测遗漏的 patch 提案
        detectMissedPatchConfirmations()
        isStreaming.value = false
        break

      case 'RUN_ERROR':
        finalizeStreamingMessage()
        isStreaming.value = false
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
    streamingText.value = ''
  }

  function handleModelThinkingStarted(_event: RuntimeEvent) {
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
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg?.role === 'assistant' && lastMsg.thinking?.status === 'thinking') {
      handleThinkingFinished(event)
    }
  }

  function handleThinkingStarted(event: RuntimeEvent) {
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

  function handleThinkingFinished(_event: RuntimeEvent) {
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
    const callId = readString(meta.callId) || readString(meta.toolCallId) || event.eventId
    const toolName = readString(meta.toolName) || readString(meta.tool) || event.stage || '未知工具'
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
    })
  }

  function handleToolCallArgsDelta(event: RuntimeEvent) {
    const meta = event.metadata || {}
    const callId = readString(meta.callId) || readString(meta.toolCallId)
    const toolCall = findToolCall(callId)
    if (!toolCall) return

    toolCall.argsText = `${toolCall.argsText || ''}${event.content || ''}`
    toolCall.args = parseToolArgs(toolCall.argsText)
  }

  function handleToolResultStarted(event: RuntimeEvent) {
    const meta = event.metadata || {}
    const callId = readString(meta.callId) || readString(meta.toolCallId) || event.eventId
    const toolName = readString(meta.toolName) || readString(meta.tool) || event.stage || '未知工具'
    let toolCall = findToolCall(callId)

    // 兜底：callId 不匹配时按工具名找
    if (!toolCall) {
      toolCall = findRunningToolCallByName(toolName)
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
      }
      lastMsg.toolCalls.push(toolCall)
    }
    toolCall.status = 'running'
  }

  function handleToolResultDelta(event: RuntimeEvent) {
    const meta = event.metadata || {}
    const callId = readString(meta.callId) || readString(meta.toolCallId)
    let toolCall = findToolCall(callId)

    // 兜底：如果 callId 不匹配，尝试找最近消息中正在运行的 toolCall
    if (!toolCall) {
      const toolName = readString(meta.toolName) || readString(meta.tool)
      toolCall = findRunningToolCallByName(toolName)
    }
    if (!toolCall) return

    toolCall.result = `${toolCall.result || ''}${event.content || ''}`
  }

  function handleToolResultFinished(event: RuntimeEvent) {
    const meta = event.metadata || {}
    const callId = readString(meta.callId) || readString(meta.toolCallId)
    let toolCall = findToolCall(callId)

    // 兜底：callId 不匹配时按工具名找最近完成的 toolCall
    if (!toolCall) {
      const toolName = readString(meta.toolName) || readString(meta.tool)
      toolCall = findRunningToolCallByName(toolName)
    }

    if (!toolCall) {
      console.warn('[ChatStore] TOOL_RESULT_FINISHED: 未找到 toolCall, callId=', callId, 'event=', event)
      return
    }

    toolCall.status = 'completed'
    toolCall.durationMs = event.elapsedMs || (Date.now() - (toolCall.startedAt || Date.now()))

    if (isPatchProposalTool(toolCall.toolName)) {
      console.log('[ChatStore] 检测到 patch 提案工具:', toolCall.toolName, 'result=', toolCall.result)
      void registerPatchConfirmation(toolCall)
    }
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
  function findRunningToolCallByName(toolName?: string): ToolCallInfo | null {
    if (!toolName) return null
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const msg = messages.value[i]
      if (!msg.toolCalls) continue
      for (let j = msg.toolCalls.length - 1; j >= 0; j--) {
        const tc = msg.toolCalls[j]
        if (tc.status === 'running' && tc.toolName === toolName) return tc
      }
    }
    return null
  }

  function readString(value: unknown): string {
    return typeof value === 'string' ? value : ''
  }

  function readNumber(value: unknown): number {
    return typeof value === 'number' && Number.isFinite(value) ? value : 0
  }

  function readArgs(value: unknown): Record<string, unknown> {
    return value && typeof value === 'object' && !Array.isArray(value)
      ? value as Record<string, unknown>
      : {}
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
    const confirmation: Confirmation = {
      patchId: readString(meta.patchId) || event.eventId,
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
    lastConversationId.value = null
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

  function normalizeBackendMessage(row: any): ChatMessage | null {
    const roleText = String(row?.role || '').toLowerCase()
    if (roleText !== 'user' && roleText !== 'assistant') return null
    return {
      id: String(row.id || `msg-${Date.now()}`),
      sessionId: String(row.conversationId || row.sessionId || ''),
      role: roleText,
      content: String(row.content || ''),
      timestamp: row.createdAt || row.updatedAt || new Date().toISOString(),
      toolCalls: normalizeBackendToolCalls(row.toolCalls),
    }
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
    currentMessages,
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
    clearSession,
    setActiveConversationId,
    restoreConversationId,
  }
})
