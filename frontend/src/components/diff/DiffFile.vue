<template>
  <div class="diff-file">
    <div class="diff-file-header">
      <i :class="changeIcon" style="font-size: 0.8rem;"></i>
      <span class="diff-file-path">{{ file.path }}</span>
      <span class="diff-file-badge" :class="file.changeType">{{ changeTypeLabel }}</span>
    </div>
    <pre class="diff-content"><code><template v-for="(line, i) in diffLines" :key="i"><span :class="diffLineClass(line)">{{ line }}
</span></template></code></pre>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PatchFile } from '@/types'

const props = defineProps<{
  file: PatchFile
  diff: string
}>()

const diffLines = computed(() => {
  // Extract lines relevant to this file from the unified diff
  const lines = props.diff.split('\n')
  const fileLines: string[] = []
  let inFile = false

  for (const line of lines) {
    if (line.startsWith('--- ') || line.startsWith('+++ ')) {
      // Check if this is our file
      const path = line.slice(6).trim() // Remove --- a/ or +++ b/
      if (path === props.file.path || path.endsWith('/' + props.file.path)) {
        inFile = true
        continue
      }
      if (inFile && (line.startsWith('--- ') || line.startsWith('+++ '))) {
        inFile = false
      }
    }
    if (line.startsWith('diff --git')) {
      const parts = line.split(' ')
      const filePath = parts[parts.length - 1]?.replace('b/', '')
      inFile = filePath === props.file.path || filePath?.endsWith('/' + props.file.path)
      continue
    }
    if (line.startsWith('@@')) {
      if (inFile) fileLines.push(line)
      continue
    }
    if (inFile) {
      fileLines.push(line)
    }
  }

  return fileLines.length > 0 ? fileLines : props.diff.split('\n')
})

const changeIcon = computed(() => {
  const icons: Record<string, string> = {
    added: 'pi pi-plus-circle',
    modified: 'pi pi-pencil',
    deleted: 'pi pi-minus-circle',
  }
  return icons[props.file.changeType] || 'pi pi-file'
})

const changeTypeLabel = computed(() => {
  const labels: Record<string, string> = {
    added: '新增',
    modified: '修改',
    deleted: '删除',
  }
  return labels[props.file.changeType] || props.file.changeType
})

function diffLineClass(line: string): string {
  if (line.startsWith('+')) return 'line-add'
  if (line.startsWith('-')) return 'line-remove'
  if (line.startsWith('@@')) return 'line-hunk'
  return 'line-context'
}
</script>

<style scoped>
.diff-file {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.diff-file-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-color);
}

.diff-file-path {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: var(--text-primary);
  font-weight: 500;
}

.diff-file-badge {
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  padding: 1px 6px;
  border-radius: 3px;
}

.diff-file-badge.added { background: #dcfce7; color: #166534; }
.diff-file-badge.modified { background: #dbeafe; color: #1e40af; }
.diff-file-badge.deleted { background: #fee2e2; color: #991b1b; }

.diff-content {
  margin: 0;
  padding: var(--spacing-sm);
  overflow-x: auto;
  font-family: var(--font-mono);
  font-size: 0.8rem;
  line-height: 1.6;
  background: var(--bg-panel);
}

.diff-content code {
  font-family: inherit;
}

.line-add {
  background: var(--bg-diff-added);
  display: block;
}

.line-remove {
  background: var(--bg-diff-removed);
  display: block;
}

.line-hunk {
  color: var(--accent);
  font-weight: 500;
  display: block;
}

.line-context {
  display: block;
}
</style>
