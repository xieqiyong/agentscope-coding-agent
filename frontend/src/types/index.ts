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
  toolCalls?: ToolCallInfo[]
  confirmation?: Confirmation
  isStreaming?: boolean
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
  | 'CANDIDATE'
  | 'ACTIVE'
  | 'CONFLICT'
  | 'DISABLED'
  | 'EXPIRED'
  | 'REJECTED'

export interface MemoryEntry {
  id: string
  workspaceId: string
  type: MemoryType
  content: string
  status: MemoryStatus
  confidence: number
  createdAt: string
  updatedAt: string
}

// ==================== Patch / Confirmation ====================

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface Confirmation {
  patchId: string
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




