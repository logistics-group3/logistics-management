import { describe, expect, it } from 'vitest'
import { ApiError, errorHelp } from './api'
import { scenarios, testAccounts, testLinks } from './test-guide'

describe('시나리오 테스트 가이드 데이터', () => {
  it('시나리오 ID는 겹치지 않는다(확인 표시 저장 키)', () => {
    expect(new Set(scenarios.map((s) => s.id)).size).toBe(scenarios.length)
  })

  it('시나리오의 시작 계정은 모두 테스트 계정 목록에 있다', () => {
    const names = new Set(testAccounts.map((a) => a.username))
    for (const scenario of scenarios.filter((s) => s.account))
      expect(names.has(scenario.account!)).toBe(true)
  })

  it('계정이 없는 시나리오는 진행 위치를 안내하고, 계정이 있으면 앱 안 경로로 연다', () => {
    for (const scenario of scenarios) {
      if (scenario.account) expect(scenario.path?.startsWith('/')).toBe(true)
      else expect(scenario.path).toBeUndefined()
      expect(scenario.steps.length).toBeGreaterThan(0)
      expect(scenario.expected.length).toBeGreaterThan(0)
    }
  })

  it('외부 화면 링크는 이 PC에서만 열리는 테스트 스택 주소다', () => {
    for (const link of testLinks) expect(link.url).toMatch(/^http:\/\/127\.0\.0\.1:1\d{4}(\/|$)/)
  })

  it('롤백 시나리오의 404는 "대상 없음"이 아니라 담당자 지정 안내를 보여 준다', () => {
    const help = errorHelp(
      new ApiError(404, 'DELIVERY_MANAGER_NOT_FOUND', '배정할 배송담당자가 없습니다.'),
    )
    expect(help).toContain('주문도 남기지 않았습니다')
    expect(errorHelp(new ApiError(404, 'ORDER_NOT_FOUND', '없음'))).toContain('존재하지 않습니다')
  })
})
