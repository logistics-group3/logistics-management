import { expect } from '@playwright/test'
import type { APIRequestContext } from '@playwright/test'
import type { Order, OrderAccepted, OrderCreation } from '../src/shared/types'

// 주문 생성은 202로 접수된 뒤 진행 조회로 완료된다. 201이면 이미 완료된 주문이다.
export async function createOrderViaApi(
  request: APIRequestContext,
  headers: Record<string, string>,
  data: unknown,
): Promise<Order> {
  const response = await request.post('/api/v1/orders', {
    headers: { ...headers, 'Idempotency-Key': crypto.randomUUID() },
    data,
  })
  expect([201, 202]).toContain(response.status())
  const body = (await response.json()).data as Order | OrderAccepted
  if (response.status() === 201) return body as Order
  return waitForCreation(request, headers, body.orderId)
}
export async function waitForCreation(
  request: APIRequestContext,
  headers: Record<string, string>,
  orderId: string,
): Promise<Order> {
  let progress: OrderCreation | undefined
  await expect
    .poll(
      async () => {
        const response = await request.get(`/api/v1/orders/creations/${orderId}`, { headers })
        expect(response.status()).toBe(200)
        progress = (await response.json()).data as OrderCreation
        return progress.status
      },
      { timeout: 60000, intervals: [1000] },
    )
    .not.toBe('PROCESSING')
  expect(progress?.status, progress?.failureMessage ?? '').toBe('COMPLETED')
  return progress!.order!
}
