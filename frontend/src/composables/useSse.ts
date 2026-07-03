import { ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useRuntimeStore } from '@/stores/runtime'
import { modelConfigApi } from '@/api/modelConfig'
import type { Confirmation, PlanInfo } from '@/types'
import type { RuntimeEvent, RuntimeEventType } from '@/types/events'

export function useSse() {
  const chatStore = useChatStore()
  const runtimeStore = useRuntimeStore()
  const abortController = ref<AbortController | null>(null)
  const error = ref<string | null>(null)

  /**
   * 发送用户消息并通过 SSE 接收后端 RuntimeEvent 流。
   *
   * 后端 SSE 格式：
   *   event: ANSWER_DELTA
   *   data: {"eventId":"...","type":"ANSWER_DELTA","stage":"回答中","content":"你好","metadata":{},"elapsedMs":123}
   *
   *   event: RUN_FINISHED
   *   data: {"eventId":"...","type":"RUN_FINISHED",...}
   */
  async function start(body: {
    workspaceId: number
    agentId?: number
    conversationId?: number
    message: string
    title?: string
    userId?: string
    maxIterations?: number
    timeoutSeconds?: number
    modelBaseUrl?: string
    modelName?: string
    apiKey?: string
    runMode?: string
    plan?: PlanInfo
    traceThinkingContent?: boolean
  }) {
    error.value = null
    abortController.value = new AbortController()

    runtimeStore.startAgentRun()

    try {
      await consumeStream('/api/agent-runtime/chat-stream', {
        ...body,
        traceThinkingContent: body.traceThinkingContent ?? true,
      })
    } catch (e: any) {
      handleStreamError(e)
    } finally {
      runtimeStore.endAgentRun()
      chatStore.finalizeStreamingMessage()
    }
  }

  async function respondApproval(confirmation: Confirmation, approved: boolean) {
    if (!confirmation.approvalId) return
    error.value = null
    abortController.value = new AbortController()
    runtimeStore.startAgentRun()

    try {
      const modelConfig = await loadModelConfig()
      await consumeStream('/api/agent-runtime/approval-stream', {
        approvalRequestId: Number(confirmation.approvalId),
        runId: confirmation.runId == null ? undefined : Number(confirmation.runId),
        approved,
        userId: '1',
        timeoutSeconds: 86400,
        modelBaseUrl: modelConfig.modelBaseUrl,
        modelName: modelConfig.modelName,
        apiKey: modelConfig.apiKey,
        traceThinkingContent: true,
      })
      chatStore.resolveConfirmation(confirmation.patchId)
    } catch (e: any) {
      handleStreamError(e)
    } finally {
      runtimeStore.endAgentRun()
      chatStore.finalizeStreamingMessage()
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

  async function consumeStream(url: string, body: unknown) {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(body),
      signal: abortController.value?.signal,
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('无法获取 SSE 流')
    }

    const decoder = new TextDecoder()
    let buffer = ''
    let currentEventType = ''
    let currentData = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEventType = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          currentData += line.slice(5).trim()
        } else if (line.trim() === '') {
          // 空行 = 事件分隔符
          if (currentEventType && currentData) {
            try {
              const event: RuntimeEvent = JSON.parse(currentData)
              dispatchEvent(currentEventType as RuntimeEventType, event)
            } catch {
              // 非 JSON data，忽略
            }
          }
          currentEventType = ''
          currentData = ''
        }
      }
    }
  }

  function handleStreamError(e: any) {
    if (e.name === 'AbortError') {
      return
    }
    const msg = e.message || 'SSE 连接失败'
    error.value = msg
    runtimeStore.recordWarning(msg)
  }

  function dispatchEvent(type: RuntimeEventType, event: RuntimeEvent) {
    // 路由到 chat store
    chatStore.handleRuntimeEvent(type, event)
    // 路由到 runtime store（右面板）
    routeToRuntimeStore(type, event)
  }

  function routeToRuntimeStore(type: RuntimeEventType, event: RuntimeEvent) {
    switch (type) {
      case 'MODEL_CALL_FINISHED': {
        const duration = event.elapsedMs || 0
        runtimeStore.recordModelCall(duration)
        break
      }
      case 'TOOL_CALL_STARTED': {
        runtimeStore.addEvent({
          id: event.eventId,
          type,
          timestamp: Date.now(),
          label: event.metadata?.toolName as string || event.stage || '工具调用',
          detail: event.content || undefined,
          severity: 'info',
        })
        break
      }
      case 'TOOL_RESULT_FINISHED': {
        const toolName = (event.metadata?.toolName as string) || '工具'
        runtimeStore.recordToolCall(toolName, event.elapsedMs || 0)
        break
      }
      case 'RUNTIME_WARNING':
        runtimeStore.recordWarning(event.content || '警告')
        break
      case 'RUN_STATUS_CHANGED':
      case 'AGENT_HANDOFF':
      case 'ROUTE_SELECTED':
      case 'PLAN_CREATED':
      case 'PLAN_STEP_STATUS_CHANGED':
        runtimeStore.addEvent({
          id: event.eventId,
          type,
          timestamp: Date.now(),
          label: event.stage || '运行状态变更',
          detail: routeEventDetail(event) || event.content || undefined,
          severity: 'info',
        })
        break
      case 'RUN_ERROR':
        runtimeStore.addEvent({
          id: event.eventId,
          type,
          timestamp: Date.now(),
          label: '运行错误',
          detail: event.content || undefined,
          severity: 'error',
        })
        break
      default:
        // 其他事件不重复记录到 runtime panel
        break
    }
  }

  function routeEventDetail(event: RuntimeEvent): string {
    if (event.type !== 'ROUTE_SELECTED') return ''
    const route = event.metadata?.effectiveRoute || event.metadata?.route
    const intent = event.metadata?.intent
    const confidence = event.metadata?.confidence
    const reason = event.content || ''
    return [
      route ? `route=${route}` : '',
      intent ? `intent=${intent}` : '',
      typeof confidence === 'number' ? `confidence=${confidence.toFixed(2)}` : '',
      reason,
    ].filter(Boolean).join(' · ')
  }

  function abort() {
    if (abortController.value) {
      abortController.value.abort()
      abortController.value = null
    }
  }

  return {
    error,
    start,
    respondApproval,
    abort,
  }
}
