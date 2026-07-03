import type { RiskLevel } from './index'

// ==================== 后端 RuntimeEventType 枚举 ====================

export type RuntimeEventType =
  | 'RUN_STARTED'
  | 'CONTEXT_LOADED'
  | 'AGENT_STARTED'
  | 'AGENT_FINISHED'
  | 'MODEL_CALL_STARTED'
  | 'MODEL_CALL_FINISHED'
  | 'ANSWER_STARTED'
  | 'ANSWER_DELTA'
  | 'ANSWER_FINISHED'
  | 'THINKING_STARTED'
  | 'THINKING_DELTA'
  | 'THINKING_FINISHED'
  | 'TOOL_CALL_STARTED'
  | 'TOOL_CALL_ARGS_DELTA'
  | 'TOOL_CALL_FINISHED'
  | 'TOOL_RESULT_STARTED'
  | 'TOOL_RESULT_DELTA'
  | 'TOOL_RESULT_DATA_DELTA'
  | 'TOOL_RESULT_FINISHED'
  | 'ROUTE_SELECTED'
  | 'PLAN_CREATED'
  | 'PLAN_STEP_STATUS_CHANGED'
  | 'AGENT_HANDOFF'
  | 'CONFIRMATION_REQUIRED'
  | 'CONFIRMATION_RESULT'
  | 'EXTERNAL_EXECUTION_REQUIRED'
  | 'EXTERNAL_EXECUTION_RESULT'
  | 'RUN_STATUS_CHANGED'
  | 'RUNTIME_WARNING'
  | 'RUN_FINISHED'
  | 'RUN_ERROR'
  | 'RAW_EVENT'

// ==================== 后端 RuntimeEvent ====================

export interface RuntimeEvent {
  eventId: string
  runId: number | null
  traceId: string | null
  type: RuntimeEventType
  stage: string | null
  content: string | null
  metadata: Record<string, unknown>
  elapsedMs: number
  createdAt: string
}

// ==================== 前端 RuntimeEvent（右面板用） ====================

export type RuntimeEventSeverity = 'info' | 'success' | 'warn' | 'error'

export interface FrontendRuntimeEvent {
  id: string
  type: RuntimeEventType
  timestamp: number
  label: string
  detail?: string
  severity: RuntimeEventSeverity
  durationMs?: number
}
