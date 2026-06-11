import { ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useRuntimeStore } from '@/stores/runtime'
import type { AgentStreamEvent } from '@/types/events'

export function useSse() {
  const chatStore = useChatStore()
  const runtimeStore = useRuntimeStore()
  const abortController = ref<AbortController | null>(null)
  const error = ref<string | null>(null)

  async function start(url: string, body: Record<string, unknown>) {
    error.value = null
    abortController.value = new AbortController()

    runtimeStore.startAgentRun()

    try {
      const response = await fetch(url, {
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
        throw new Error('No readable stream available')
      }

      const decoder = new TextDecoder()
      let buffer = ''
      let currentEventType = ''
      let currentData = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // Parse SSE lines
        const lines = buffer.split('\n')
        buffer = lines.pop() || '' // Keep incomplete line in buffer

        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEventType = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            currentData += line.slice(5).trim()
          } else if (line === '' || line === '\r') {
            // Empty line = event delimiter
            if (currentEventType && currentData) {
              try {
                const payload = JSON.parse(currentData)
                const event: AgentStreamEvent = {
                  type: currentEventType as AgentStreamEvent['type'],
                  timestamp: Date.now(),
                  payload,
                }
                dispatchEvent(event)
              } catch {
                // Non-JSON data, ignore
              }
            }
            currentEventType = ''
            currentData = ''
          }
        }
      }
    } catch (e: any) {
      if (e.name === 'AbortError') {
        // User cancelled, not an error
      } else {
        const msg = e.message || 'SSE 连接失败'
        error.value = msg
        runtimeStore.recordWarning(msg)
      }
    } finally {
      runtimeStore.endAgentRun()
      chatStore.handleEvent({
        type: 'agent_finished',
        timestamp: Date.now(),
        payload: {},
      })
    }
  }

  function dispatchEvent(event: AgentStreamEvent) {
    // Route to chat store
    chatStore.handleEvent(event)

    // Route to runtime store for observability
    switch (event.type) {
      case 'model_call_started':
        // Track start time internally
        break
      case 'model_call_finished': {
        const payload = event.payload as { durationMs?: number }
        if (payload.durationMs) {
          runtimeStore.recordModelCall(payload.durationMs)
        }
        break
      }
      case 'tool_call_started': {
        const payload = event.payload as { callId: string; toolName: string }
        runtimeStore.addEvent({
          id: `evt-${Date.now()}-${payload.callId}`,
          type: event.type,
          timestamp: Date.now(),
          label: payload.toolName,
          detail: `started`,
          severity: 'info',
        })
        break
      }
      case 'tool_result_delta': {
        const payload = event.payload as { callId: string; resultDelta: string; toolName?: string }
        // We'll update duration in tool_call_started tracking; for now just note it
        break
      }
      case 'runtime_warning': {
        const payload = event.payload as { message: string }
        runtimeStore.recordWarning(payload.message)
        break
      }
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
