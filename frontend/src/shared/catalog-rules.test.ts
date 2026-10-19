import { describe, expect, it } from 'vitest'
import {
  buildCompanyRequest,
  canManageCompany,
  deliveryManagerRequest,
  parseWonPrice,
} from './catalog-rules'
import { serializeRequest } from './api'
import type { Company, User } from './types'
const user = { userId: 'me', role: 'USER' } as User
const master = { userId: 'master', role: 'MASTER' } as User
const values = { name: '새 업체', address: '서울시 송파구', hubId: 'hub', managerId: 'other' }
describe('업체 등록·담당자 요청 경계', () => {
  it('USER 등록·수정에는 다른 사람의 managerId를 전송하지 않는다', () =>
    expect(buildCompanyRequest(values, user)).toEqual({
      name: '새 업체',
      address: '서울시 송파구',
      hubId: 'hub',
    }))
  it('MASTER는 담당자를 반드시 선택한다', () => {
    expect(() => buildCompanyRequest({ ...values, managerId: '' }, master)).toThrow('담당자')
    expect(buildCompanyRequest(values, master).managerId).toBe('other')
  })
  it('공백뿐인 이름과 주소는 전송하지 않는다', () => {
    expect(() => buildCompanyRequest({ ...values, name: ' ' }, user)).toThrow()
    expect(() => buildCompanyRequest({ ...values, address: '\t' }, user)).toThrow()
  })
  it('담당자가 다른 USER에게 업체 수정 동작을 제공하지 않는다', () => {
    const company = { managerId: 'other' } as Company
    expect(canManageCompany(user, company)).toBe(false)
    expect(canManageCompany(master, company)).toBe(true)
    expect(canManageCompany(user, { managerId: 'me' } as Company)).toBe(true)
  })
  it('배송 담당 해제에는 hubId와 role을 전송하지 않는다', () => {
    expect(deliveryManagerRequest(false, 'old-hub')).toEqual({ isDeliveryManager: false })
    expect(deliveryManagerRequest(true, 'hub')).toEqual({ isDeliveryManager: true, hubId: 'hub' })
    expect(() => deliveryManagerRequest(true, '')).toThrow('허브')
  })
})
describe('상품 원화 입력과 JSON 전송', () => {
  it('long 최대 가격을 반올림하거나 문자열 JSON으로 바꾸지 않는다', () =>
    expect(serializeRequest({ price: parseWonPrice('9223372036854775807') })).toBe(
      '{"price":9223372036854775807}',
    ))
  it.each(['-1', '1.5', '1e3', '1,000', '', '9223372036854775808'])('%s 가격을 거절한다', (price) =>
    expect(() => parseWonPrice(price)).toThrow(),
  )
  it('0원과 앞자리 0을 안전하게 처리한다', () => {
    expect(parseWonPrice('0')).toBe(0n)
    expect(parseWonPrice('00010')).toBe(10n)
  })
})
