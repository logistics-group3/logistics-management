export type View = 'task' | 'diy' | 'notes'

export type NoteMeta = { file: string; title: string }

export type Snapshot = {
  branch: string
  progressPath: string
  hasProgress: boolean
  sections: Record<string, string>
  notes: NoteMeta[]
}

declare module 'claude-code' {
  interface PluginState {
    'study-mode': {
      view: View
      snapshot: Snapshot
      selected: string
      body: string
    }
  }
}
