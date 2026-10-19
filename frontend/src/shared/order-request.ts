import { ApiError } from './api'
import type { Order, OrderAccepted, OrderCreation, OrderInput } from './types'
export interface PendingOrder {
  key: string
  body: OrderInput
  // 202 접수 뒤에 저장한다. 새로고침 후에는 POST 없이 이 주문의 진행만 조회한다.
  orderId?: string
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
export const CREATION_POLL_INTERVAL_MS = 1000
export const CREATION_POLL_LIMIT_MS = 60000
// Note: api()는 HTTP 상태를 버리므로 201(완료된 주문)과 202(접수)는 data 모양으로 구분한다.
export function classifyCreation(
  data: Order | OrderAccepted | null | undefined,
): { kind: 'created'; order: Order } | { kind: 'accepted'; orderId: string } | { kind: 'unknown' } {
  if (!data?.orderId) return { kind: 'unknown' }
  if (data.status === 'PROCESSING' && !('deliveryId' in data))
    return { kind: 'accepted', orderId: data.orderId }
  if ('deliveryId' in data && data.deliveryId) return { kind: 'created', order: data as Order }
  return { kind: 'unknown' }
}
export type CreationStep = 'completed' | 'aborted' | 'recovery' | 'continue' | 'timeout'
// 종료 상태를 시간 초과보다 먼저 본다. COMPLETED인데 주문이 없으면 결과를 확정할 수 없다.
export function creationStep(
  progress: OrderCreation,
  elapsedMs: number,
  limitMs = CREATION_POLL_LIMIT_MS,
): CreationStep {
  if (progress.status === 'COMPLETED') return progress.order ? 'completed' : 'recovery'
  if (progress.status === 'ABORTED') return 'aborted'
  if (progress.status === 'RECOVERY_REQUIRED') return 'recovery'
  return elapsedMs >= limitMs ? 'timeout' : 'continue'
}
