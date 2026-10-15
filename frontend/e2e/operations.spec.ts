import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import fs from 'node:fs'
const data = JSON.parse(fs.readFileSync('../.local/ui-test-data.json', 'utf8'))
const password = 'UiLocal!2026'
async function login(page: Page, username = 'uimaster') {
  await page.getByLabel('아이디', { exact: true }).fill(username)
  await page.getByLabel('비밀번호', { exact: true }).fill(password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '운영 콘솔 로그인', exact: true })).toHaveCount(0)
}
async function createOrder(page: Page, note: string) {
  await page.getByRole('link', { name: '새 주문 등록' }).click()
  const form = page.getByRole('dialog', { name: '새 주문 등록' })
  await form.getByLabel('공급업체', { exact: true }).selectOption(data.supplierCompanyId)
  await form.getByLabel('수령업체', { exact: true }).selectOption(data.receiverCompanyId)
  await form.getByLabel('상품', { exact: true }).selectOption(data.products[0].productId)
  await form.getByLabel('주문 수량', { exact: true }).fill('3')
  await form.getByLabel('배송 요청사항 (선택)').fill(note)
  await form.getByRole('button', { name: '주문 등록', exact: true }).click()
}
async function removeOrder(page: Page) {
  await page.getByRole('button', { name: '주문 삭제', exact: true }).click()
  await page.getByRole('button', { name: '삭제 확인', exact: true }).click()
  await expect(page).toHaveURL(/\/orders$/)
  await expect(page.getByRole('status')).toContainText('주문이 삭제되었습니다.')
}
async function openNav(page: Page, name: string) {
  await page
    .getByRole('navigation', { name: '주 메뉴' })
    .getByRole('link', { name, exact: true })
    .click()
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
}

test('로그인, 전체 메뉴 조회, 검색, 빈 결과와 데스크톱 접근성', async ({ page }, testInfo) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/')
  await page.screenshot({ path: testInfo.outputPath('login.png'), fullPage: true })
  await page.getByLabel('아이디', { exact: true }).fill('invalid')
  await page.getByLabel('비밀번호', { exact: true }).fill('Wrong!123')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await login(page)
  await expect(page.getByRole('heading', { name: '물류 운영 현황' })).toBeVisible()
  await expect(page.locator('.stat-card').first()).not.toContainText('—')
  await page.screenshot({ path: testInfo.outputPath('dashboard.png'), fullPage: true })
  await page
    .locator('[role=dialog]')
    .evaluateAll((elements) =>
      Promise.all(
        elements.flatMap((el) => el.getAnimations({ subtree: true }).map((a) => a.finished)),
      ),
    )
  const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
  expect(audit.violations.map((v) => ({ id: v.id, nodes: v.nodes.map((n) => n.target) }))).toEqual(
    [],
  )
  for (const name of [
    '주문 관리',
    '배송 관리',
    '업체 관리',
    '상품 관리',
    '허브 관리',
    '사용자 관리',
  ]) {
    await openNav(page, name)
    await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
  }
  await openNav(page, '업체 관리')
  await page.getByRole('searchbox').fill('없는 업체 검색 테스트')
  await page.getByRole('button', { name: '검색', exact: true }).click()
  await expect(page.getByText('조건에 맞는 데이터가 없습니다.')).toBeVisible()
  expect(errors).toEqual([])
})

test('주문 생성 → 요청사항 수정 → 취소 → MASTER 삭제', async ({ page }, testInfo) => {
  await page.goto('/orders')
  await login(page)
  await createOrder(page, 'E2E 생성·취소 테스트')
  const detail = page.getByRole('dialog', { name: '주문 상세' })
  await expect(detail).toBeVisible()
  await expect(detail.getByText('55,500원', { exact: true })).toBeVisible()
  await detail.getByLabel('배송 요청사항', { exact: true }).fill('입고 전 10분 전에 연락 바랍니다.')
  await detail.getByRole('button', { name: '요청사항 저장' }).click()
  await expect(page.getByRole('status')).toContainText('요청사항이 수정되었습니다.')
  await page.screenshot({ path: testInfo.outputPath('order-detail.png'), fullPage: true })
  await detail.getByRole('button', { name: '주문 취소', exact: true }).click()
  await page.getByRole('button', { name: '돌아가기', exact: true }).click()
  await expect(detail.getByRole('button', { name: '주문 취소', exact: true })).toBeFocused()
  await detail.getByRole('button', { name: '주문 취소', exact: true }).click()
  await page.getByRole('button', { name: '취소 확인', exact: true }).click()
  await expect(detail.getByLabel('배송 요청사항', { exact: true })).toBeDisabled()
  await removeOrder(page)
})

test('배송 2구간 출발·도착, 집하 후 취소 거절, 완료 후 삭제', async ({ page }, testInfo) => {
  await page.goto('/orders')
  await login(page)
  await createOrder(page, 'E2E 배송 완료 테스트')
  await expect(page.getByRole('dialog', { name: '주문 상세' })).toBeVisible()
  const orderUrl = page.url()
  await page.getByRole('link', { name: '배송 상세', exact: true }).click()
  await expect(page.locator('.route-step')).toHaveCount(2)
  for (const [index, action] of ['출발 처리', '도착 처리', '출발 처리', '도착 처리'].entries()) {
    await page.getByRole('button', { name: action, exact: true }).click()
    await page.getByRole('button', { name: '상태 변경 확인', exact: true }).click()
    await expect(page.locator('.detail-hero .badge')).toHaveText(
      ['집하 중', '도착 허브 입고', '업체 배송 중', '배송 완료'][index],
    )
    if (index === 0) {
      await page.getByRole('link', { name: /ORD-/ }).click()
      await page.getByRole('button', { name: '주문 취소', exact: true }).click()
      await page.getByRole('button', { name: '취소 확인', exact: true }).click()
      await expect(page.getByRole('alert')).toBeVisible()
      await page.getByRole('link', { name: '배송 상세', exact: true }).click()
    }
  }
  await page.screenshot({ path: testInfo.outputPath('delivery-completed.png'), fullPage: true })
  await expect(page.getByRole('button', { name: '출발 처리', exact: true })).toHaveCount(0)
  await page.getByRole('link', { name: /ORD-/ }).click()
  await expect(page).toHaveURL(orderUrl)
  await removeOrder(page)
})

test('생성 응답 유실 → 새로고침·재로그인 → 동일 키로 재확인, 주문은 1건', async ({ page }) => {
  await page.goto('/orders')
  await login(page)
  const requests: { key: string | undefined; body: string | null }[] = []
  let orderId = ''
  let interrupted = false
  await page.route('**/api/v1/orders', async (route) => {
    if (route.request().method() !== 'POST') return route.continue()
    requests.push({
      key: route.request().headers()['idempotency-key'],
      body: route.request().postData(),
    })
    if (!interrupted) {
      interrupted = true
      const response = await route.fetch()
      expect(response.status()).toBe(201)
      orderId = (await response.json()).data.orderId
      return route.abort('failed')
    }
    return route.continue()
  })
  await createOrder(page, 'E2E 응답 유실 테스트')
  await expect(page.getByRole('button', { name: '같은 요청 결과 확인' })).toBeVisible()
  await page.reload()
  await login(page)
  await expect(page.getByRole('dialog').getByLabel('주문 수량', { exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '같은 요청 결과 확인' }).click()
  await expect(page).toHaveURL(new RegExp(`/orders/${orderId}$`))
  expect(requests).toHaveLength(2)
  expect(requests[0]).toEqual(requests[1])
  expect(requests[0].key).toBeTruthy()
  await page.getByRole('button', { name: '주문 취소', exact: true }).click()
  await page.getByRole('button', { name: '취소 확인', exact: true }).click()
  await expect(
    page.getByRole('dialog', { name: '주문 상세' }).getByLabel('배송 요청사항', { exact: true }),
  ).toBeDisabled()
  await removeOrder(page)
})

test('USER는 자기 주문·프로필, MASTER 삭제 제한과 타인 주문 403', async ({ page }) => {
  await page.goto('/orders')
  await login(page, 'uiuser')
  await page.getByRole('link', { name: /^ORD-/ }).first().click()
  await expect(page.getByRole('dialog', { name: '주문 상세' })).toBeVisible()
  await expect(page.getByRole('button', { name: '주문 삭제', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '닫기', exact: true }).click()
  await openNav(page, '사용자 관리')
  await expect(page.getByRole('heading', { name: '내 프로필', exact: true })).toBeVisible()
  await page.goto(`/orders/${data.orders[0]}`)
  await login(page, 'uiuser')
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('권한')
})

test('모바일 메뉴·폼·키보드, 레이아웃과 접근성', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await login(page)
  await expect(page.getByRole('navigation', { name: '주 메뉴' })).toHaveCount(0)
  await page.getByRole('button', { name: '메뉴 열기' }).click()
  await openNav(page, '주문 관리')
  await expect(page.getByRole('heading', { name: '주문 관리' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('mobile-orders.png'), fullPage: true })
  await page.getByRole('link', { name: '새 주문 등록' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.keyboard.press('Tab')
  expect(
    await page.evaluate(() =>
      document.querySelector('[role="dialog"]')?.contains(document.activeElement),
    ),
  ).toBe(true)
  await page
    .locator('[role=dialog]')
    .evaluateAll((elements) =>
      Promise.all(
        elements.flatMap((el) => el.getAnimations({ subtree: true }).map((a) => a.finished)),
      ),
    )
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
  expect(audit.violations.map((v) => ({ id: v.id, nodes: v.nodes.map((n) => n.target) }))).toEqual(
    [],
  )
  await page.screenshot({ path: testInfo.outputPath('mobile-create.png'), fullPage: false })
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.locator('#page-title')).toBeFocused()
})

test('주문 페이지 이동·상태 필터와 조회 장애 후 복구', async ({ page }) => {
  await page.goto('/orders')
  await login(page)
  const first = await page.getByRole('link', { name: /^ORD-/ }).first().getAttribute('href')
  await page.getByRole('button', { name: '다음 페이지' }).click()
  await expect(page.getByRole('button', { name: '이전 페이지' })).toBeEnabled()
  await expect(page.getByRole('link', { name: /^ORD-/ }).first()).not.toHaveAttribute(
    'href',
    first!,
  )
  await page
    .getByRole('group', { name: '주문 상태 필터' })
    .getByRole('button', { name: '취소', exact: true })
    .click()
  await expect(page.locator('tbody .badge').first()).toHaveText('취소')
  await expect(page.getByRole('button', { name: '이전 페이지' })).toBeDisabled()
  await page.route('**/api/v1/orders?**', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: '테스트용 일시 장애' }),
    }),
  )
  await page.getByRole('button', { name: '주문 목록 새로고침' }).click()
  await expect(page.getByRole('alert')).toContainText('테스트용 일시 장애')
  await page.unroute('**/api/v1/orders?**')
  await page.getByRole('button', { name: '다시 조회' }).click()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await page.goto('/orders/00000000-0000-0000-0000-000000000000')
  await login(page)
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('존재하지')
})

test('배송담당자의 배정 구간 처리와 세션 만료', async ({ page, request }) => {
  const auth = await request.post('/api/v1/auth/login', {
    data: { username: 'uimaster', password },
  })
  const token = (await auth.json()).data.accessToken
  const headers = { Authorization: `Bearer ${token}`, 'Idempotency-Key': crypto.randomUUID() }
  const response = await request.post('/api/v1/orders', {
    headers,
    data: {
      supplierCompanyId: data.supplierCompanyId,
      receiverCompanyId: data.receiverCompanyId,
      productId: data.products[0].productId,
      quantity: 1,
      requestNote: 'E2E 배송담당자 테스트',
    },
  })
  expect(response.status()).toBe(201)
  const order = (await response.json()).data
  await page.goto(`/deliveries/${order.deliveryId}`)
  await login(page, 'uidriver')
  await expect(page.getByText('이준호 (나)', { exact: false }).first()).toBeVisible()
  for (const [index, action] of ['출발 처리', '도착 처리', '출발 처리', '도착 처리'].entries()) {
    await page.getByRole('button', { name: action, exact: true }).click()
    await page.getByRole('button', { name: '상태 변경 확인', exact: true }).click()
    await expect(page.locator('.detail-hero .badge')).toHaveText(
      ['집하 중', '도착 허브 입고', '업체 배송 중', '배송 완료'][index],
    )
  }
  expect((await request.delete(`/api/v1/orders/${order.orderId}`, { headers })).status()).toBe(200)
  await page.getByRole('button', { name: '닫기', exact: true }).click()
  await page.route('**/api/v1/deliveries?**', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'UNAUTHORIZED', message: '세션 만료' }),
    }),
  )
  await page.getByRole('button', { name: '새로고침', exact: true }).click()
  await expect(page.getByRole('heading', { name: '운영 콘솔 로그인', exact: true })).toBeVisible()
  await expect(page.getByText('세션이 만료되었습니다.', { exact: false })).toBeVisible()
})

test('회원가입 검증·중복 아이디·가입 완료 → 실제 로그인·내 프로필', async ({
  page,
  request,
}, testInfo) => {
  await page.goto('/login')
  await page.getByRole('link', { name: '회원가입', exact: true }).click()
  await expect(page.getByRole('heading', { name: '회원가입', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '계정 만들기', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('입력 항목')
  await expect(page.getByLabel('아이디', { exact: true })).toBeFocused()
  await page.getByLabel('아이디', { exact: true }).fill('uiuser')
  await page.getByLabel('비밀번호', { exact: true }).fill(password)
  await page.getByLabel('비밀번호 확인', { exact: true }).fill('Different!1')
  await page.getByLabel('이름', { exact: true }).fill('가입 검증 담당자')
  await page.getByLabel('Slack 멤버 ID', { exact: true }).fill('UI_E2E_SIGNUP')
  await page.getByLabel('소속명', { exact: true }).fill('회원가입 테스트 업체')
  await page.getByRole('button', { name: '계정 만들기', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('비밀번호가 일치하지')
  await page.getByLabel('비밀번호 확인', { exact: true }).fill(password)
  await page.getByRole('button', { name: '계정 만들기', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('이미 사용 중인 아이디')
  const username = 'ui' + Date.now().toString(36).slice(-8)
  await page.getByLabel('아이디', { exact: true }).fill(username)
  await page.screenshot({ path: testInfo.outputPath('signup-desktop.png'), fullPage: true })
  const created = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/v1/auth/signup') && response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '계정 만들기', exact: true }).click()
  const response = await created
  expect(response.status()).toBe(201)
  const account = (await response.json()).data
  expect(account.role).toBe('USER')
  expect(account.isDeliveryManager).toBe(false)
  try {
    await expect(page.getByRole('heading', { name: '운영 콘솔 로그인', exact: true })).toBeVisible()
    await expect(page.getByLabel('아이디', { exact: true })).toHaveValue(username)
    await expect(page.getByText('회원가입이 완료되었습니다.', { exact: false })).toBeVisible()
    await login(page, username)
    await openNav(page, '사용자 관리')
    await expect(page.getByText('가입 검증 담당자 / ' + username)).toBeVisible()
    await expect(page.getByText('업체 · 회원가입 테스트 업체')).toBeVisible()
    await openNav(page, '주문 관리')
    await page.getByRole('link', { name: '새 주문 등록', exact: true }).click()
    await expect(page.getByText('연결된 수령업체가 없습니다.', { exact: false })).toBeVisible()
  } finally {
    const auth = await request.post('/api/v1/auth/login', {
      data: { username: 'uimaster', password },
    })
    const token = (await auth.json()).data.accessToken
    expect(
      (
        await request.delete(`/api/v1/users/${account.userId}`, {
          headers: { Authorization: `Bearer ${token}` },
        })
      ).status(),
    ).toBe(200)
  }
})

test('모바일 회원가입 화면과 접근성', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/signup')
  await expect(page.getByRole('heading', { name: '회원가입', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
  expect(audit.violations.map((v) => ({ id: v.id, nodes: v.nodes.map((n) => n.target) }))).toEqual(
    [],
  )
  await page.screenshot({ path: testInfo.outputPath('signup-mobile.png'), fullPage: true })
})
