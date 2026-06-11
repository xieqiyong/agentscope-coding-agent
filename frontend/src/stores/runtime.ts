import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { FrontendRuntimeEvent, RuntimeEventType } from '@/types/events'

export const useRuntimeStore = defineStore('runtime', () => {
  const events = ref<FrontendRuntimeEvent[]>([])
  const modelCallCount = ref(0)
  const toolCallCount = ref(0)
  const agentStartTime = ref<number | null>(null)
  const agentEndTime = ref<number | null>(null)
  const totalModelTimeMs = ref(0)
  const totalToolTimeMs = ref(0)
  const perToolStats = ref<Map<string, { count: number; totalTimeMs: number }>>(new Map())

  const totalAgentTimeMs = computed(() => {
    if (!agentStartTime.value) return 0
    const end = agentEndTime.value || Date.now()
    return end - agentStartTime.value
  })

  const isAgentRunning = computed(() => agentStartTime.value !== null && agentEndTime.value === null)

  function addEvent(event: FrontendRuntimeEvent) {
    events.value.push(event)
  }

  function recordModelCall(durationMs: number) {
    modelCallCount.value++
    totalModelTimeMs.value += durationMs
    addEvent({
      id: `evt-${Date.now()}-${modelCallCount.value}`,
      type: 'MODEL_CALL_FINISHED',
      timestamp: Date.now(),
      label: `模型调用 #${modelCallCount.value}`,
      detail: `${durationMs}ms`,
      severity: 'success',
      durationMs,
    })
  }

  function recordToolCall(toolName: string, durationMs: number) {
    toolCallCount.value++
    totalToolTimeMs.value += durationMs

    const existing = perToolStats.value.get(toolName) || { count: 0, totalTimeMs: 0 }
    existing.count++
    existing.totalTimeMs += durationMs
    perToolStats.value.set(toolName, existing)

    addEvent({
      id: `evt-${Date.now()}-${toolCallCount.value}`,
      type: 'TOOL_RESULT_FINISHED',
      timestamp: Date.now(),
      label: `${toolName}`,
      detail: `完成，耗时 ${durationMs}ms`,
      severity: 'info',
      durationMs,
    })
  }

  function recordWarning(message: string) {
    addEvent({
      id: `evt-${Date.now()}-warn`,
      type: 'RUNTIME_WARNING',
      timestamp: Date.now(),
      label: '警告',
      detail: message,
      severity: 'warn',
    })
  }

  function startAgentRun() {
    reset()
    agentStartTime.value = Date.now()
    agentEndTime.value = null
    addEvent({
      id: `evt-${Date.now()}-start`,
      type: 'RUN_STARTED',
      timestamp: Date.now(),
      label: 'Agent 启动',
      severity: 'info',
    })
  }

  function endAgentRun() {
    agentEndTime.value = Date.now()
    addEvent({
      id: `evt-${Date.now()}-end`,
      type: 'RUN_FINISHED',
      timestamp: Date.now(),
      label: 'Agent 完成',
      detail: `总耗时 ${totalAgentTimeMs.value}ms`,
      severity: 'success',
      durationMs: totalAgentTimeMs.value,
    })
  }

  function reset() {
    events.value = []
    modelCallCount.value = 0
    toolCallCount.value = 0
    agentStartTime.value = null
    agentEndTime.value = null
    totalModelTimeMs.value = 0
    totalToolTimeMs.value = 0
    perToolStats.value = new Map()
  }

  return {
    events,
    modelCallCount,
    toolCallCount,
    agentStartTime,
    agentEndTime,
    totalModelTimeMs,
    totalToolTimeMs,
    perToolStats,
    totalAgentTimeMs,
    isAgentRunning,
    addEvent,
    recordModelCall,
    recordToolCall,
    recordWarning,
    startAgentRun,
    endAgentRun,
    reset,
  }
})
