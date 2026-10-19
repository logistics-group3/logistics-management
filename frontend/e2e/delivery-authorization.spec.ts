import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import type { DeliveryDetail, Order, User } from '../src/shared/types'

const fixture = JSON.parse(readFileSync('../.local/ui-test-data.json', 'utf8'))
const password = 'UiLocal!2026'

async function login(page: Page, username: string, deliveryId: string) {
  await page.goto(`/deliveries/${deliveryId}`)
  await page.getByLabel('아이디', { exact: true }).fill(username)
  await page.getByLabel('비밀번호', { exact: true }).fill(password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '운영 콘솔 로그인', exact: true })).toHaveCount(0)
}

test('담당자 해제 반영 후 기존 토큰·UI 변경 차단, MASTER 재배정과 새 담당자 처리', async ({
  page,
  request,
}, testInfo) => {
  test.setTimeout(180000)
  async function call<T>(method: string, path: string, token?: string, body?: unknown) {
    const response = await request.fetch(`/api/v1${path}`, {
      method,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      data: body,
    })
    expect(response.ok(), `${method} ${path}: ${response.status()}`).toBeTruthy()
    return (await response.json()).data as T
  }
  const tokenFor = async (username: string) =>
    (await call<{ accessToken: string }>('POST', '/auth/login', undefined, { username, password }))
      .accessToken
  const master = await tokenFor('uimaster')
  const original = await call<User>('GET', `/users/${fixture.users.uidriver}`, master)
  expect(original.isDeliveryManager).toBe(true)
  expect((await call<User>('GET', '/users/me', master)).isDeliveryManager).toBe(false)
  const driverToken = await tokenFor('uidriver')
  const replacementName = `r13${Date.now().toString(36).slice(-7)}`
  let replacementId: string | undefined
  let order: Order | undefined
  let released = false
  try {
    const response = await request.post('/api/v1/orders', {
      headers: { Authorization: `Bearer ${master}`, 'Idempotency-Key': crypto.randomUUID() },
      data: {
        supplierCompanyId: fixture.supplierCompanyId,
        receiverCompanyId: fixture.receiverCompanyId,
        productId: fixture.products[0].productId,
        quantity: 1,
        requestNote: 'E2E #13 담당자 해제·인계 검증',
      },
    })
    expect(response.status()).toBe(201)
    order = (await response.json()).data
    const deliveryId = order!.deliveryId
    const before = await call<DeliveryDetail>('GET', `/deliveries/${deliveryId}`, master)
    expect(before.routes.every((route) => route.managerId === original.userId)).toBe(true)
    await login(page, 'uidriver', deliveryId)
    await expect(page.getByRole('button', { name: '출발 처리', exact: true })).toBeVisible()

    await call('PATCH', `/users/${original.userId}/role`, master, { isDeliveryManager: false })
    released = true
    // Note: /users/me는 최신 DB 값이라 Gateway 캐시 만료의 증거가 아니다.
    // 잘못된 WAITING 전이로 데이터 변경 없이 자격이 true면 409, false면 403인지 확인한다.
    await expect
      .poll(
        async () =>
          (
            await request.patch(
              `/api/v1/deliveries/${deliveryId}/routes/${before.routes[0].deliveryRouteId}/status`,
              {
                headers: { Authorization: `Bearer ${driverToken}` },
                data: { status: 'WAITING' },
              },
            )
          ).status(),
        { timeout: 75000, intervals: [1000, 2000] },
      )
      .toBe(403)

    for (const [path, body] of [
      [
        `/deliveries/${deliveryId}/routes/${before.routes[0].deliveryRouteId}/status`,
        { status: 'MOVING' },
      ],
      [
        `/deliveries/${deliveryId}`,
        { deliveryAddress: '차단돼야 하는 주소', recipientName: '차단돼야 하는 수령인' },
      ],
    ] as const) {
      const denied = await request.patch(`/api/v1${path}`, {
        headers: { Authorization: `Bearer ${driverToken}` },
        data: body,
      })
      expect(denied.status()).toBe(403)
      expect((await denied.json()).code).toBe('FORBIDDEN')
    }
    expect(await call<DeliveryDetail>('GET', `/deliveries/${deliveryId}`, master)).toEqual(before)
    await login(page, 'uidriver', deliveryId)
    await expect(page.locator('.route-step')).toHaveCount(before.routes.length)
    await expect(page.getByRole('button', { name: /^(출발|도착) 처리$/ })).toHaveCount(0)
    await page.screenshot({ path: testInfo.outputPath('released-driver.png') })

    const signedUp = await call<{ userId: string }>('POST', '/auth/signup', undefined, {
      username: replacementName,
      password,
      name: '권한 회귀 대체 담당자',
      affiliationType: 'HUB',
      affiliationName: original.affiliationName,
      slackEmail: `${replacementName}@example.com`,
    })
    replacementId = signedUp.userId
    await call('PATCH', `/users/${replacementId}/role`, master, {
      isDeliveryManager: true,
      hubId: original.hubId,
    })
    for (const route of before.routes) {
      await call(
        'POST',
        `/deliveries/${deliveryId}/routes/${route.deliveryRouteId}/manager`,
        master,
        { managerId: replacementId },
      )
    }
    await login(page, replacementName, deliveryId)
    await page.getByRole('button', { name: '출발 처리', exact: true }).click()
    await page.getByRole('button', { name: '상태 변경 확인', exact: true }).click()
    await expect(page.locator('.detail-hero .badge')).toHaveText('집하 중')
    await login(page, 'uimaster', deliveryId)
    await expect(page.getByRole('button', { name: '도착 처리', exact: true })).toBeVisible()
  } finally {
    // 테스트 실패 시에도 원래 담당자와 신규 계정·주문을 정리한다. 기존 시드 주문은 변경하지 않는다.
    if (released) {
      await call('PATCH', `/users/${original.userId}/role`, master, {
        isDeliveryManager: true,
        hubId: original.hubId,
      })
      if (order) {
        const delivery = await call<DeliveryDetail>(
          'GET',
          `/deliveries/${order.deliveryId}`,
          master,
        )
        const unfinished = delivery.routes.find(
          (route) => route.status === 'WAITING' || route.status === 'MOVING',
        )
        if (unfinished) {
          const path = `/deliveries/${order.deliveryId}/routes/${unfinished.deliveryRouteId}`
          await call('POST', `${path}/manager`, master, { managerId: original.userId })
          // 다음 테스트가 기존 담당자를 바로 쓸 수 있도록 캐시의 true 반영도 확인한다.
          await expect
            .poll(
              async () =>
                (
                  await request.patch(`/api/v1${path}/status`, {
                    headers: { Authorization: `Bearer ${driverToken}` },
                    data: { status: 'WAITING' },
                  })
                ).status(),
              { timeout: 75000, intervals: [1000, 2000] },
            )
            .toBe(409)
        }
      }
    }
    if (order) {
      const delivery = await call<DeliveryDetail>('GET', `/deliveries/${order.deliveryId}`, master)
      if (delivery.status === 'PICKUP_WAITING') {
        await call('POST', `/orders/${order.orderId}/cancel`, master)
      } else {
        for (const route of delivery.routes) {
          for (const status of route.status === 'WAITING'
            ? ['MOVING', 'ARRIVED']
            : route.status === 'MOVING'
              ? ['ARRIVED']
              : []) {
            await call(
              'PATCH',
              `/deliveries/${order.deliveryId}/routes/${route.deliveryRouteId}/status`,
              master,
              { status },
            )
          }
        }
      }
      await call('DELETE', `/orders/${order.orderId}`, master)
    }
    if (replacementId) await call('DELETE', `/users/${replacementId}`, master)
  }
})
