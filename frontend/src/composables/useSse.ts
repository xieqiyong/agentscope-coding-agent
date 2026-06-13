import { ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useRuntimeStore } from '@/stores/runtime'
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
  }) {
    error.value = null
    abortController.value = new AbortController()

    runtimeStore.startAgentRun()

    try {
      const response = await fetch('/api/agent-runtime/chat-stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify(body),
        signal: abortController.value.signal,
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
    } catch (e: any) {
      if (e.name === 'AbortError') {
        // 用户取消
      } else {
        const msg = e.message || 'SSE 连接失败'
        error.value = msg
        runtimeStore.recordWarning(msg)
      }
    } finally {
      runtimeStore.endAgentRun()
      chatStore.finalizeStreamingMessage()
    }
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
        runtimeStore.addEvent({
          id: event.eventId,
          type,
          timestamp: Date.now(),
          label: event.stage || '运行状态变更',
          detail: event.content || undefined,
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

  function abort() {
    if (abortController.value) {
      abortController.value.abort()
      abortController.value = null
    }
  }

  return {
    error,
    start,
    abort,
  }
}
