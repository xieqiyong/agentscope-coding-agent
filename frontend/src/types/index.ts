// ==================== Workspace ====================

export interface Workspace {
  id: string
  name: string
  rootPath: string
  description?: string
  status: 'ACTIVE' | 'INACTIVE'
  createdAt: string
  updatedAt: string
}

export interface FileNode {
  key: string
  label: string
  path: string
  isDirectory: boolean
  size?: number
  modifiedAt?: string
  children?: FileNode[]
}

// ==================== Session ====================

export interface Session {
  id: string
  workspaceId: string
  title: string
  createdAt: string
  updatedAt: string
}

// ==================== Chat ====================

export interface ChatMessage {
  id: string
  sessionId: string
  role: 'user' | 'assistant'
  content: string
  timestamp: string
  thinking?: ThinkingInfo
  toolCalls?: ToolCallInfo[]
  plan?: PlanInfo
  confirmation?: Confirmation
  isStreaming?: boolean
}

export interface PlanInfo {
  title: string
  summary: string
  riskLevel: RiskLevel
  steps: PlanStep[]
  acceptanceCriteria: string[]
  expectedTools: string[]
  requiresApproval: boolean
}

export interface PlanStep {
  id: string
  title: string
  description?: string
  status: 'pending' | 'in_progress' | 'completed' | 'failed'
  agentName?: string
  tools: string[]
}

export interface ThinkingInfo {
  status: 'thinking' | 'done'
  content?: string
  omitted?: boolean
  chars?: number
  startedAt?: number
  durationMs?: number
}

export interface ToolCallInfo {
  callId: string
  toolName: string
  args: Record<string, unknown>
  argsText?: string
  result?: string
  status: 'running' | 'completed' | 'error'
  startedAt?: number
  durationMs?: number
  patchId?: string
}

// ==================== Memory ====================

export type MemoryType =
  | 'USER_PREFERENCE'
  | 'PROJECT_FACT'
  | 'PROJECT_CONSTRAINT'
  | 'WORKING_STYLE'
  | 'VERIFIED_EXPERIENCE'
  | 'SKILL_REFERENCE'

export type MemoryStatus =
  | 'PENDING'
  | 'ACTIVE'
  | 'CONFLICT'
  | 'DISABLED'
  | 'REJECTED'

export interface MemoryEntry {
  id: string
  workspaceId: string
  agentId?: string
  userId?: string
  type: MemoryType
  memoryType?: MemoryType
  normalizedKey?: string
  content: string
  status: MemoryStatus
  confidence: number
  reviewReason?: string
  sourceConversationId?: string
  sourceMessageId?: string
  createdAt: string
  updatedAt: string
}

// ==================== Patch / Confirmation ====================

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface Confirmation {
  patchId: string
  kind?: 'PATCH' | 'TOOL_PERMISSION'
  approvalId?: string
  runId?: number | string | null
  toolName?: string
  toolCallId?: string
  files: PatchFile[]
  diff: string
  riskLevel: RiskLevel
  summary: string
}

export interface PatchFile {
  path: string
  changeType: 'added' | 'modified' | 'deleted'
  additions: number
  deletions: number
}




