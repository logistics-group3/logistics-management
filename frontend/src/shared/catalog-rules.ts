import type { Company, User } from './types'
export interface CompanyFields {
  name: string
  address: string
  hubId: string
  managerId: string
}
export function canManageCompany(user: User, company: Company) {
  return user.role === 'MASTER' || company.managerId === user.userId
}
export function buildCompanyRequest(
  values: CompanyFields,
  user: User,
): { name: string; address: string; hubId: string; managerId?: string } {
  if (!values.name.trim() || values.name.length > 100)
    throw new Error('업체명을 1~100자로 입력해 주세요.')
  if (!values.address.trim() || values.address.length > 255)
    throw new Error('주소를 1~255자로 입력해 주세요.')
  if (!values.hubId) throw new Error('관리 허브를 선택해 주세요.')
  if (user.role === 'MASTER' && !values.managerId) throw new Error('업체 담당자를 선택해 주세요.')
  // Note: USER의 담당자는 서버가 로그인 사용자로 정한다. 관리자가 아닌 요청에 managerId를 싣지 않는다.
  return {
    name: values.name,
    address: values.address,
    hubId: values.hubId,
    ...(user.role === 'MASTER' ? { managerId: values.managerId } : {}),
  }
}
export function deliveryManagerRequest(enabled: boolean, hubId: string) {
  if (enabled && !hubId) throw new Error('배송을 담당할 허브를 선택해 주세요.')
  return enabled ? { isDeliveryManager: true, hubId } : { isDeliveryManager: false }
}
export function parseWonPrice(value: string): bigint {
  if (!/^\d+$/.test(value))
    throw new Error('가격은 0 이상의 정수로 입력해 주세요. 쉼표와 소수점은 제외합니다.')
  const price = BigInt(value)
  if (price > 9223372036854775807n)
    throw new Error('가격이 서버의 원화 정수 허용 범위를 초과합니다.')
  return price
}
