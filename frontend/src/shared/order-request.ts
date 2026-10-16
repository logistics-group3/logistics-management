import { ApiError } from './api'
import type { OrderInput } from './types'
export interface PendingOrder {
  key: string
  body: OrderInput
  createdAt: string
}
const storageKey = (userId: string) => `sparta:order:v1:${userId}`
export function savePending(userId: string, pending: PendingOrder) {
  sessionStorage.setItem(storageKey(userId), JSON.stringify(pending))
}
export function loadPending(userId: string): PendingOrder | null {
  const raw = sessionStorage.getItem(storageKey(userId))
  if (!raw) return null
  const value = JSON.parse(raw) as PendingOrder
  if (!value.key || !value.body?.productId || !Number.isInteger(value.body.quantity))
    throw new Error(
      '저장된 주문 요청을 읽을 수 없습니다. 이 탭을 유지하고 관리자에게 확인해 주세요.',
    )
  return value
}
export function clearPending(userId: string) {
  sessionStorage.removeItem(storageKey(userId))
}
export function creationFailure(error: unknown): 'edit' | 'retry' | 'blocked' | 'aborted' {
  if (!(error instanceof ApiError)) return 'retry'
  if (error.code === 'ORDER_CREATION_ABORTED') return 'aborted'
  if (['ORDER_CREATION_RECOVERY_REQUIRED', 'ORDER_IDEMPOTENCY_KEY_CONFLICT'].includes(error.code))
    return 'blocked'
  if (error.status === 0 || error.status >= 500 || error.status === 401 || error.status === 409)
    return 'retry'
  return 'edit'
}
