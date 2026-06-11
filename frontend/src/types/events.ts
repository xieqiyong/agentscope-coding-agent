import type { Confirmation, RiskLevel } from './index'

// ==================== SSE Event Types ====================

export type AgentEventType =
  | 'agent_started'
  | 'agent_finished'
  | 'model_call_started'
  | 'model_call_finished'
  | 'answer_delta'
  | 'tool_call_started'
  | 'tool_call_args_delta'
  | 'tool_result_delta'
  | 'runtime_warning'
  | 'confirmation_required'

export interface AgentStreamEvent {
  type: AgentEventType
  timestamp: number
  payload: unknown
}

// ==================== Specific Payloads ====================

export interface AgentStartedPayload {
  sessionId: string
}

export interface AgentFinishedPayload {
  sessionId: string
  totalDurationMs?: number
}

export interface ModelCallStartedPayload {
  model: string
}

export interface ModelCallFinishedPayload {
  durationMs: number
  tokenUsage?: {
    promptTokens?: number
    completionTokens?: number
    totalTokens?: number
  }
}

export interface AnswerDeltaPayload {
  delta: string
}

export interface ToolCallStartedPayload {
  callId: string
  toolName: string
  args: Record<string, unknown>
}

export interface ToolCallArgsDeltaPayload {
  callId: string
  argsDelta: string
}

export interface ToolResultDeltaPayload {
  callId: string
  resultDelta: string
}

export interface RuntimeWarningPayload {
  message: string
  details?: string
}

export interface ConfirmationRequiredPayload {
  patchId: string
  files: Confirmation['files']
  diff: string
  riskLevel: RiskLevel
  summary: string
}

// ==================== Runtime Event (frontend) ====================

export type RuntimeEventSeverity = 'info' | 'success' | 'warn' | 'error'

export interface RuntimeEvent {
  id: string
  type: AgentEventType
  timestamp: number
  label: string
  detail?: string
  severity: RuntimeEventSeverity
  durationMs?: number
}
