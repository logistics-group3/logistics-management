import { atom, read, update } from 'claude-code'
import type { EngineInterface, Register } from 'claude-code'

import type { NoteMeta, Snapshot, View } from '../types'

const PANE = 'study'
const TITLE = '학습 모드'
const QNA_DIR = 'docs/learning/qna'
const WATCHED = ['docs/progress/', `${QNA_DIR}/`]

const EMPTY: Snapshot = { branch: '', progressPath: '', hasProgress: false, sections: {}, notes: [] }

const view = atom({ plugin: 'study-mode', key: 'view' } as const, 'task' as View)
const snapshot = atom({ plugin: 'study-mode', key: 'snapshot' } as const, EMPTY)
const selected = atom({ plugin: 'study-mode', key: 'selected' } as const, '')
const body = atom({ plugin: 'study-mode', key: 'body' } as const, '')

export const parseSections = (markdown: string): Record<string, string> => {
  const sections: Record<string, string> = {}
  const parts = markdown.split(/^## +(.+)$/m)
  for (let i = 1; i < parts.length; i += 2) {
    sections[(parts[i] ?? '').trim()] = (parts[i + 1] ?? '').trim()
  }
  return sections
}

export const noteTitle = (markdown: string, file: string): string =>
  markdown.match(/^title:\s*(.+)$/m)?.[1]?.trim() ?? file.replace(/\.md$/, '')

const readText = async ($: EngineInterface, path: string): Promise<string | undefined> => {
  try {
    const text = await $.fs.read(path)
    return typeof text === 'string' ? text : undefined
  } catch {
    return undefined
  }
}

const currentBranch = async ($: EngineInterface): Promise<string> => {
  try {
    const { exitCode, stdout } = await $.process.run(['git', 'branch', '--show-current'])
    return exitCode === 0 ? stdout.trim() : ''
  } catch {
    return ''
  }
}

const listNotes = async ($: EngineInterface): Promise<NoteMeta[]> => {
  try {
    const entries = await $.fs.list(QNA_DIR)
    const files = entries
      .filter(entry => entry.kind === 'file' && entry.name.endsWith('.md'))
      .map(entry => entry.name)
      .sort()
      .reverse()
    return Promise.all(
      files.map(async file => ({ file, title: noteTitle((await readText($, `${QNA_DIR}/${file}`)) ?? '', file) })),
    )
  } catch {
    return []
  }
}

const selectNote = async ($: EngineInterface, file: string) => {
  await update($, selected, () => file)
  await update($, body, () => '')
  const text = await readText($, `${QNA_DIR}/${file}`)
  await update($, body, () => text ?? '노트를 읽지 못했다.')
}

const refresh = async ($: EngineInterface) => {
  const branch = await currentBranch($)
  const progressPath = branch ? `docs/progress/${branch.replaceAll('/', '-')}.md` : ''
  const progress = progressPath ? await readText($, progressPath) : undefined
  const notes = await listNotes($)
  const before = await read($, snapshot)

  await update($, snapshot, () => ({
    branch,
    progressPath,
    hasProgress: progress !== undefined,
    sections: progress ? parseSections(progress) : {},
    notes,
  }))

  const current = progress ? parseSections(progress)['지금 하는 일']?.split('\n')[0] : undefined
  await $.ui.status(current ? `작업: ${current}` : undefined)

  const known = new Set(before.notes.map(note => note.file))
  const added = before.branch ? notes.find(note => !known.has(note.file)) : undefined
  if (added) {
    await selectNote($, added.file)
    await update($, view, () => 'notes' as View)
    void $.ui.open({ id: PANE, title: TITLE })
  } else if (!(await read($, selected)) && notes[0]) {
    await selectNote($, notes[0].file)
  }
}

const touchesWatched = (call: unknown): boolean => {
  const text = JSON.stringify(call ?? {})
  return WATCHED.some(dir => text.includes(dir)) || /git (switch|checkout)/.test(text)
}

export const register: Register = on => {
  on('session.start', async ($, e, next) => {
    await $.command.register({ name: 'study', description: '학습 패널(현재 작업, 직접 해보기, 지식 노트)을 연다' })
    await refresh($)
    void $.ui.open({ id: PANE, title: TITLE })
    return next(e)
  })

  on('command.run', { command: 'study' }, async $ => {
    await refresh($)
    await $.ui.open({ id: PANE, title: TITLE })
    return { text: '학습 패널을 열었다.' }
  })

  on('tool.call', async ($, e, next) => {
    const result = await next(e)
    if (['Write', 'Edit', 'MultiEdit', 'Bash'].includes(e.tool) && touchesWatched(e)) {
      await refresh($)
    }
    return result
  }).catch(($, e, next) => next(e))

  on('ui.render', { component: 'Pane', requestId: PANE }, async ($, e) => {
    const elements = $.ui.resolve(e)
    const { Box, Text, Button, Markdown } = elements
    const current = await read($, view)
    const snap = await read($, snapshot)
    const tab = (id: View, label: string, hotkey: string) => (
      <Button key={`tab-${id}`} label={current === id ? `[${label}]` : label} hotkey={hotkey}
        onPress={() => update($, view, () => id)} />
    )

    const missing = (
      <Text dimColor>
        {snap.progressPath || '브랜치 없음'} 파일이 없다. handoff 스킬로 만든다.
      </Text>
    )
    const section = (...names: string[]) =>
      names
        .filter(name => snap.sections[name])
        .map(name => `### ${name}\n${snap.sections[name]}`)
        .join('\n\n')

    let content
    if (current === 'notes') {
      const file = await read($, selected)
      const text = await read($, body)
      const options = snap.notes.map(note => ({ value: note.file, label: `${note.file.slice(0, 10)} ${note.title}` }))
      // 모바일 surface에는 Select가 없어서 버튼 목록으로 대신한다
      const picker = options.length === 0
        ? undefined
        : 'Select' in elements
        ? <elements.Select key="note" label="노트" value={file || options[0]?.value}
            options={options} onSelect={(value: string) => selectNote($, value)} />
        : (
          <Box flexDirection="column">
            {options.map(option => (
              <Button key={`note-${option.value}`} label={option.label} onPress={() => selectNote($, option.value)} />
            ))}
          </Box>
        )
      content = options.length === 0
        ? <Text dimColor>아직 저장된 노트가 없다.</Text>
        : (
          <Box flexDirection="column">
            {picker}
            <Markdown text={text || '불러오는 중'} />
          </Box>
        )
    } else if (!snap.hasProgress) {
      content = missing
    } else if (current === 'diy') {
      content = <Markdown text={snap.sections['직접 해보기'] || '직접 해보기 섹션이 비어 있다.'} />
    } else {
      content = <Markdown text={section('지금 하는 일', '진행 상태', '다음 단계')} />
    }

    return (
      <Box flexDirection="column">
        <Box flexDirection="row" gap={2}>
          {tab('task', '현재 작업', '1')}
          {tab('diy', '직접 해보기', '2')}
          {tab('notes', `지식 노트 ${snap.notes.length}`, '3')}
        </Box>
        <Text dimColor>{snap.branch ? `브랜치 ${snap.branch}` : ''}</Text>
        {content}
      </Box>
    )
  })
}
