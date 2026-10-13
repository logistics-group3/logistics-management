import { describe, expect, test } from 'claude-code/testing'

import { noteTitle, parseSections } from '../hooks/register'

const SURFACES = ['terminal', 'desktop'] as const

describe('progress 파싱', () => {
  test('## 제목 단위로 섹션을 나눈다', () => {
    const sections = parseSections('# b\n\n## 지금 하는 일\nEureka 서버 세팅\n\n## 직접 해보기\n1. a\n2. b\n')
    expect(sections['지금 하는 일']).toBe('Eureka 서버 세팅')
    expect(sections['직접 해보기']).toBe('1. a\n2. b')
  })

  test('노트 제목은 frontmatter title, 없으면 파일명', () => {
    expect(noteTitle('---\ntitle: Gradle toolchain\n---', 'x.md')).toBe('Gradle toolchain')
    expect(noteTitle('본문만 있음', '2026-10-13-x.md')).toBe('2026-10-13-x')
  })
})

describe('패널', () => {
  test('progress 파일이 없으면 안내하고, 탭으로 노트 화면에 간다', async $ => {
    for (const surface of SURFACES) {
      const ui = await $.ui.mount({
        plugin: 'study-mode', surface, component: 'Pane', requestId: 'study', props: { title: '학습 모드', isFocused: true, bodyColumns: 80, placement: 'dock', scroll: { offset: 0, bodyRows: 30 }, view: {} },
      })
      expect(await ui.find({ text: 'handoff 스킬로 만든다' })).toBeDefined()
      await ui.press({ key: 'tab-notes' })
      expect(await ui.find({ text: '아직 저장된 노트가 없다.' })).toBeDefined()
      await ui.press({ key: 'tab-task' })
    }
  })
})
