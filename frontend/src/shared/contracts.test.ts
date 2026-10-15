import { validateSignup } from './signup'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ApiError, parseResponse } from './api'
import { money } from './format'
import { clearPending, creationFailure, loadPending, savePending } from './order-request'
import { nextRouteAction } from './delivery-rules'
import type { DeliveryDetail, DeliveryRoute, User } from './types'
describe('원화 정수 정밀도', () => {
  it('Java long 최댓값과 JS 안전 정수 밖의 원화를 원문 그대로 표시한다', () => {
    const data = parseResponse(
      '{"unitPrice":9007199254740993,"totalPrice":9223372036854775807,"quantity":2}',
    ) as { unitPrice: string; totalPrice: string; quantity: number }
    expect(data.unitPrice).toBe('9007199254740993')
    expect(money(data.totalPrice)).toBe('9,223,372,036,854,775,807원')
    expect(data.quantity).toBe(2)
  })
  it('0원과 안전한 단가도 같은 문자열 계약을 쓴다', () => {
    expect(parseResponse('{"price":0}')).toEqual({ price: '0' })
    expect(money('18500')).toBe('18,500원')
  })
  it('잘못된 JSON 응답을 성공으로 취급하지 않는다', () =>
    expect(() => parseResponse('<html>error</html>')).toThrow())
})
describe('미완료 주문 보존', () => {
  beforeEach(() => {
    const store = new Map<string, string>()
    vi.stubGlobal('sessionStorage', {
      setItem: (k: string, v: string) => store.set(k, v),
      getItem: (k: string) => store.get(k) ?? null,
      removeItem: (k: string) => store.delete(k),
    })
  })
  it('재진입 때 원문과 키를 유지하고 계정마다 분리한다', () => {
    const request = {
      key: 'key-1',
      createdAt: '2026-10-15',
      body: {
        supplierCompanyId: 's',
        receiverCompanyId: 'r',
        productId: 'p',
        quantity: 3,
        requestNote: '원문',
      },
    }
    savePending('user-a', request)
    expect(loadPending('user-a')).toEqual(request)
    expect(loadPending('user-b')).toBeNull()
    clearPending('user-a')
    expect(loadPending('user-a')).toBeNull()
  })
  it.each([0, 401, 500, 503])('HTTP %i에서는 새 키를 발급하지 않는다', (status) =>
    expect(creationFailure(new ApiError(status, 'ERROR', ''))).toBe('retry'),
  )
  it.each(['ORDER_CREATION_RECOVERY_REQUIRED', 'ORDER_IDEMPOTENCY_KEY_CONFLICT'])(
    '%s를 새 주문으로 우회하지 않는다',
    (code) => expect(creationFailure(new ApiError(409, code, ''))).toBe('blocked'),
  )
  it('확정된 생성 종료는 사용자가 새 요청을 작성할 수 있다', () =>
    expect(creationFailure(new ApiError(409, 'ORDER_CREATION_ABORTED', ''))).toBe('aborted'))
  it('입력 검증 거절은 수정할 수 있다', () =>
    expect(creationFailure(new ApiError(400, 'VALIDATION_ERROR', ''))).toBe('edit'))
})
describe('배송 구간 액션', () => {
  const user = { userId: 'driver', role: 'USER' } as User
  const first = {
    sequence: 1,
    status: 'WAITING',
    managerId: 'driver',
    routeType: 'PICKUP',
  } as DeliveryRoute
  const last = {
    sequence: 2,
    status: 'WAITING',
    managerId: 'driver',
    routeType: 'LAST_MILE',
  } as DeliveryRoute
  const delivery = { status: 'PICKUP_WAITING', routes: [first, last] } as DeliveryDetail
  it('같은 허브의 2구간 배송도 앞 구간 도착 후 출발할 수 있다', () => {
    expect(nextRouteAction(delivery, first, user)).toBe('MOVING')
    expect(nextRouteAction(delivery, last, user)).toBeNull()
    expect(
      nextRouteAction({ ...delivery, routes: [{ ...first, status: 'ARRIVED' }, last] }, last, user),
    ).toBe('MOVING')
  })
  it('담당자 외 USER에게 변경 액션을 주지 않는다', () =>
    expect(nextRouteAction(delivery, first, { ...user, userId: 'other' })).toBeNull())
  it('MASTER는 담당자가 아니어도 처리할 수 있다', () =>
    expect(nextRouteAction(delivery, first, { ...user, role: 'MASTER', userId: 'admin' })).toBe(
      'MOVING',
    ))
  it('취소된 배송과 완료된 배송은 변경할 수 없다', () => {
    expect(nextRouteAction({ ...delivery, status: 'CANCELED' }, first, user)).toBeNull()
    expect(nextRouteAction({ ...delivery, status: 'DELIVERED' }, first, user)).toBeNull()
  })
})

describe('회원가입 입력 계약', () => {
  const valid = {
    username: 'newuser1',
    password: 'UiLocal!2026',
    passwordConfirm: 'UiLocal!2026',
    name: '테스트 담당자',
    slackId: 'UI_TEST_MEMBER',
    affiliationType: 'COMPANY' as const,
    affiliationName: '테스트 업체',
  }
  it('서버 규칙에 맞는 가입 정보를 허용한다', () => expect(validateSignup(valid)).toEqual({}))
  it.each(['ABC123', 'abc', 'abcdefghijkl'])('아이디 %s를 거절한다', (username) =>
    expect(validateSignup({ ...valid, username }).username).toBeTruthy(),
  )
  it.each(['short1!', 'alllower!2026', 'ALLUPPER!2026', 'NoNumbers!', 'NoSymbols2026'])(
    '비밀번호 조합 규칙을 검증한다',
    (password) =>
      expect(
        validateSignup({ ...valid, password, passwordConfirm: password }).password,
      ).toBeTruthy(),
  )
  it('확인 비밀번호가 다르면 가입 전에 알린다', () =>
    expect(
      validateSignup({ ...valid, passwordConfirm: 'Different!1' }).passwordConfirm,
    ).toBeTruthy())
  it('이름·Slack ID·소속명의 공백만 있는 값을 거절한다', () =>
    expect(
      Object.keys(validateSignup({ ...valid, name: ' ', slackId: '\t', affiliationName: '  ' })),
    ).toEqual(['name', 'slackId', 'affiliationName']))
})
