import { test, expect } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import fs from 'node:fs'
const fixture = JSON.parse(fs.readFileSync('../.local/ui-test-data.json', 'utf8'))
const password = 'UiLocal!2026'
const unique = (prefix: string) => prefix + Date.now().toString(36).slice(-7)
async function adminHeaders(request: APIRequestContext) {
  const response = await request.post('/api/v1/auth/login', {
    data: { username: 'uimaster', password },
  })
  expect(response.status()).toBe(200)
  return { Authorization: `Bearer ${(await response.json()).data.accessToken}` }
}
async function login(page: Page, username: string) {
  await expect(page.getByRole('heading', { name: '운영 콘솔 로그인', exact: true })).toBeVisible()
  await page.getByLabel('아이디', { exact: true }).fill(username)
  await page.getByLabel('비밀번호', { exact: true }).fill(password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '운영 콘솔 로그인' })).toHaveCount(0)
}
async function nav(page: Page, name: string) {
  await page
    .getByRole('navigation', { name: '주 메뉴' })
    .getByRole('link', { name, exact: true })
    .click()
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
}
async function signup(request: APIRequestContext, username: string, name: string) {
  const response = await request.post('/api/v1/auth/signup', {
    data: {
      username,
      password,
      name,
      slackEmail: 'driver@example.com',
      affiliationType: 'COMPANY',
      affiliationName: 'UI 담당 연결 검증',
    },
  })
  expect(response.status()).toBe(201)
  return (await response.json()).data
}
async function companyThroughUI(page: Page, name: string, hubId: string) {
  await nav(page, '업체 관리')
  await page.getByRole('button', { name: '업체 등록', exact: true }).click()
  const form = page.getByRole('dialog', { name: '업체 등록', exact: true })
  await form.getByLabel('업체명', { exact: true }).fill(name)
  await form.getByLabel('관리 허브', { exact: true }).selectOption(hubId)
  await form.getByLabel('업체 주소', { exact: true }).fill('서울특별시 송파구 UI 테스트 입고장')
  const response = page.waitForResponse(
    (r) => r.url().endsWith('/api/v1/companies') && r.request().method() === 'POST',
  )
  await form.getByRole('button', { name: '업체 등록 완료' }).click()
  const created = await response
  expect(created.status()).toBe(201)
  const company = (await created.json()).data
  await expect(page.getByRole('dialog', { name: '업체 상세' })).toBeVisible()
  await expect(page.getByRole('dialog').getByText(name, { exact: true }).last()).toBeVisible()
  return company
}
async function closeSheet(page: Page) {
  await page.getByRole('button', { name: '닫기', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
}
async function cleanup(
  request: APIRequestContext,
  resources: { orders: string[]; companies: string[]; users: string[] },
) {
  const headers = await adminHeaders(request)
  for (const id of resources.orders) {
    const result = await request.get(`/api/v1/orders/${id}`, { headers })
    if (result.status() === 404) continue
    const order = (await result.json()).data
    if (order.status === 'ORDERED') {
      const delivery = (
        await (await request.get(`/api/v1/deliveries/${order.deliveryId}`, { headers })).json()
      ).data
      if (delivery.status === 'PICKUP_WAITING')
        await request.post(`/api/v1/orders/${id}/cancel`, { headers })
      else if (delivery.status !== 'DELIVERED')
        for (const route of delivery.routes.sort(
          (a: { sequence: number }, b: { sequence: number }) => a.sequence - b.sequence,
        )) {
          if (route.status === 'WAITING')
            await request.patch(
              `/api/v1/deliveries/${order.deliveryId}/routes/${route.deliveryRouteId}/status`,
              { headers, data: { status: 'MOVING' } },
            )
          if (route.status !== 'ARRIVED')
            await request.patch(
              `/api/v1/deliveries/${order.deliveryId}/routes/${route.deliveryRouteId}/status`,
              { headers, data: { status: 'ARRIVED' } },
            )
        }
    }
    expect((await request.delete(`/api/v1/orders/${id}`, { headers })).status()).toBe(200)
  }
  for (const id of resources.companies)
    expect((await request.delete(`/api/v1/companies/${id}`, { headers })).status()).toBe(200)
  for (const id of resources.users)
    expect((await request.delete(`/api/v1/users/${id}`, { headers })).status()).toBe(200)
}

test('신규 회원가입 → 본인 업체 2곳 등록 → 상품 등록 → 실제 주문 생성', async ({
  page,
  request,
}, testInfo) => {
  const resources = { orders: [] as string[], companies: [] as string[], users: [] as string[] }
  const username = unique('un')
  try {
    await page.goto('/signup')
    await page.getByLabel('아이디', { exact: true }).fill(username)
    await page.getByLabel('비밀번호', { exact: true }).fill(password)
    await page.getByLabel('비밀번호 확인', { exact: true }).fill(password)
    await page.getByLabel('이름', { exact: true }).fill('신규 업무 담당자')
    await page.getByLabel('Slack 계정 이메일', { exact: true }).fill('onboarding@example.com')
    await page.getByLabel('소속명', { exact: true }).fill('가입부터 주문 검증')
    const registration = page.waitForResponse(
      (r) => r.url().endsWith('/auth/signup') && r.request().method() === 'POST',
    )
    await page.getByRole('button', { name: '계정 만들기' }).click()
    const account = (await (await registration).json()).data
    resources.users.push(account.userId)
    await login(page, username)
    await expect(page.getByRole('heading', { name: '내 업체 등록부터 시작하세요.' })).toBeVisible()
    const supplier = await companyThroughUI(page, `E2E 공급 ${username}`, fixture.hubId)
    resources.companies.push(supplier.companyId)
    expect(supplier.managerId).toBe(account.userId)
    await expect(
      page
        .getByRole('dialog')
        .getByRole('definition')
        .filter({ hasText: `신규 업무 담당자 (${username}) · 나` }),
    ).toBeVisible()
    await expect(page.getByRole('button', { name: '업체 정보·담당자 변경' })).toHaveCount(0)
    await page.getByRole('button', { name: '업체 정보 수정' }).click()
    await expect(page.getByLabel('업체 담당자', { exact: true })).toHaveCount(0)
    await page
      .getByLabel('업체 주소', { exact: true })
      .fill('서울특별시 송파구 UI 테스트 출고장 2층')
    await page.getByRole('button', { name: '업체 변경 저장' }).click()
    await expect(
      page.getByRole('dialog').getByText('서울특별시 송파구 UI 테스트 출고장 2층', { exact: true }),
    ).toBeVisible()
    await page.getByRole('link', { name: '이 업체에 상품 등록' }).click()
    const productForm = page.getByRole('dialog', { name: '상품 등록', exact: true })
    await expect(productForm.getByLabel('공급업체', { exact: true })).toHaveValue(
      supplier.companyId,
    )
    await productForm.getByLabel('상품명', { exact: true }).fill(`E2E 원화 정밀도 ${username}`)
    await productForm.getByLabel('판매 단가 (원)', { exact: true }).fill('9007199254740993')
    const productResponse = page.waitForResponse(
      (r) =>
        r.url().endsWith(`/companies/${supplier.companyId}/products`) &&
        r.request().method() === 'POST',
    )
    await productForm.getByRole('button', { name: '상품 등록 완료' }).click()
    const createdProduct = await productResponse
    expect(createdProduct.request().postData()).toContain('"price":9007199254740993')
    expect(createdProduct.status()).toBe(201)
    const product = (await createdProduct.json()).data
    await expect(
      page
        .getByRole('dialog', { name: '상품 상세' })
        .getByText('9,007,199,254,740,993원', { exact: true }),
    ).toBeVisible()
    await closeSheet(page)
    const receiver = await companyThroughUI(page, `E2E 수령 ${username}`, fixture.hubId)
    resources.companies.push(receiver.companyId)
    await page.screenshot({ path: testInfo.outputPath('new-company-connected.png') })
    await page.getByRole('link', { name: '주문 등록으로' }).click()
    const orderForm = page.getByRole('dialog', { name: '새 주문 등록' })
    await orderForm.getByLabel('공급업체', { exact: true }).selectOption(supplier.companyId)
    await orderForm.getByLabel('수령업체', { exact: true }).selectOption(receiver.companyId)
    await orderForm.getByLabel('상품', { exact: true }).selectOption(product.productId)
    await orderForm.getByLabel('주문 수량', { exact: true }).fill('1')
    const orderResponse = page.waitForResponse(
      (r) => r.url().endsWith('/api/v1/orders') && r.request().method() === 'POST',
    )
    await orderForm.getByRole('button', { name: '주문 등록', exact: true }).click()
    const result = await orderResponse
    expect(result.status()).toBe(201)
    const order = (await result.json()).data
    resources.orders.push(order.orderId)
    expect(order.ordererId).toBe(account.userId)
    await expect(
      page.getByRole('dialog', { name: '주문 상세' }).locator('.price-total'),
    ).toContainText('9,007,199,254,740,993원')
    await expect(page.locator('.delivery-summary')).toContainText('집하 대기')
    await page.screenshot({ path: testInfo.outputPath('onboarding-order-created.png') })
  } finally {
    await cleanup(request, resources)
  }
})

test('MASTER 업체 담당자 이전 → 새 담당자 주문 생성, 다른 USER 수정 거절', async ({
  page,
  request,
}, testInfo) => {
  const resources = { orders: [] as string[], companies: [] as string[], users: [] as string[] }
  const username = unique('um')
  try {
    const account = await signup(request, username, '연결할 업체 담당자')
    resources.users.push(account.userId)
    await page.goto('/companies')
    await login(page, 'uimaster')
    const company = await companyThroughUI(page, `E2E 담당자 연결 ${username}`, fixture.hubId)
    resources.companies.push(company.companyId)
    await page.getByRole('button', { name: '업체 정보·담당자 변경' }).click()
    await page.getByLabel('담당자 이름·아이디 검색', { exact: true }).fill(username)
    await page.getByLabel('업체 담당자', { exact: true }).selectOption(account.userId)
    await expect(page.getByText('이 업체의 관리 권한을', { exact: false })).toBeVisible()
    await page.getByRole('button', { name: '업체 변경 저장' }).click()
    await expect(
      page
        .getByRole('dialog')
        .getByRole('definition')
        .filter({ hasText: `연결할 업체 담당자 (${username})` }),
    ).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('assigned-company-manager.png') })
    await closeSheet(page)
    await page.getByRole('button', { name: '로그아웃' }).click()
    await login(page, username)
    await nav(page, '주문 관리')
    await page.getByRole('link', { name: '새 주문 등록', exact: true }).click()
    const form = page.getByRole('dialog', { name: '새 주문 등록' })
    await form.getByLabel('공급업체', { exact: true }).selectOption(fixture.supplierCompanyId)
    await form.getByLabel('수령업체', { exact: true }).selectOption(company.companyId)
    await form.getByLabel('상품', { exact: true }).selectOption(fixture.products[0].productId)
    const creation = page.waitForResponse(
      (r) => r.url().endsWith('/api/v1/orders') && r.request().method() === 'POST',
    )
    await form.getByRole('button', { name: '주문 등록', exact: true }).click()
    const result = await creation
    expect(result.status()).toBe(201)
    resources.orders.push((await result.json()).data.orderId)
    const auth = await request.post('/api/v1/auth/login', {
      data: { username: 'uiviewer', password },
    })
    const forbidden = await request.patch(`/api/v1/companies/${company.companyId}`, {
      headers: { Authorization: `Bearer ${(await auth.json()).data.accessToken}` },
      data: { managerId: fixture.users.uiviewer },
    })
    expect(forbidden.status()).toBe(403)
  } finally {
    await cleanup(request, resources)
  }
})

test('MASTER 배송담당자·허브 연결 → 실제 자동 배정과 구간 처리 → 지정 해제', async ({
  page,
  request,
}, testInfo) => {
  const resources = { orders: [] as string[], companies: [] as string[], users: [] as string[] }
  const username = unique('ud')
  try {
    const account = await signup(request, username, '새 배송 담당자')
    resources.users.push(account.userId)
    const headers = await adminHeaders(request)
    const hubs = (await (await request.get('/api/v1/hubs?size=50', { headers })).json()).data
      .content
    const hub = hubs.find((h: { hubId: string }) => h.hubId !== fixture.hubId)
    await page.goto('/users')
    await login(page, 'uimaster')
    await page.getByRole('searchbox').fill('새 배송 담당자')
    await page.getByRole('button', { name: '검색', exact: true }).click()
    await page.getByRole('button', { name: '새 배송 담당자 상세', exact: true }).click()
    const detail = page.getByRole('dialog', { name: '사용자 상세' })
    await detail.getByRole('checkbox', { name: '배송 담당자로 지정' }).check()
    await detail.getByLabel('담당 허브', { exact: true }).selectOption(hub.hubId)
    const assignment = page.waitForResponse(
      (r) => r.url().endsWith(`/users/${account.userId}/role`) && r.request().method() === 'PATCH',
    )
    await detail.getByRole('button', { name: '배송 담당 설정 저장' }).click()
    const assigned = await assignment
    expect(assigned.status()).toBe(200)
    expect(JSON.parse(assigned.request().postData()!)).toEqual({
      isDeliveryManager: true,
      hubId: hub.hubId,
    })
    expect((await assigned.json()).data.role).toBe('USER')
    await expect(detail.getByText(`${hub.name} 담당자`, { exact: true })).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('delivery-manager-connected.png') })
    const companies = []
    for (const name of ['배송 출고', '배송 입고']) {
      const response = await request.post('/api/v1/companies', {
        headers,
        data: {
          name: `E2E ${name} ${username}`,
          hubId: hub.hubId,
          address: 'UI 테스트 주소',
          managerId: fixture.users.uimaster,
        },
      })
      expect(response.status()).toBe(201)
      const company = (await response.json()).data
      companies.push(company)
      resources.companies.push(company.companyId)
    }
    const product = (
      await (
        await request.post(`/api/v1/companies/${companies[0].companyId}/products`, {
          headers,
          data: { name: 'E2E 배정 상품', price: 1000 },
        })
      ).json()
    ).data
    const orderResponse = await request.post('/api/v1/orders', {
      headers: { ...headers, 'Idempotency-Key': crypto.randomUUID() },
      data: {
        supplierCompanyId: companies[0].companyId,
        receiverCompanyId: companies[1].companyId,
        productId: product.productId,
        quantity: 1,
        requestNote: 'E2E 담당 배정',
      },
    })
    expect(orderResponse.status()).toBe(201)
    const order = (await orderResponse.json()).data
    resources.orders.push(order.orderId)
    const delivery = (
      await (await request.get(`/api/v1/deliveries/${order.deliveryId}`, { headers })).json()
    ).data
    expect(
      delivery.routes.every((r: { managerId: string }) => r.managerId === account.userId),
    ).toBe(true)
    await closeSheet(page)
    await page.getByRole('button', { name: '로그아웃' }).click()
    await login(page, username)
    await nav(page, '배송 관리')
    await page.getByRole('link', { name: /^DLV-/ }).first().click()
    for (const [index, action] of ['출발 처리', '도착 처리', '출발 처리', '도착 처리'].entries()) {
      await page.getByRole('button', { name: action, exact: true }).click()
      await page.getByRole('button', { name: '상태 변경 확인' }).click()
      await expect(page.locator('.detail-hero .badge')).toHaveText(
        ['집하 중', '도착 허브 입고', '업체 배송 중', '배송 완료'][index],
      )
    }
    await closeSheet(page)
    await page.getByRole('button', { name: '로그아웃' }).click()
    await login(page, 'uimaster')
    await nav(page, '사용자 관리')
    await page.getByRole('searchbox').fill('새 배송 담당자')
    await page.getByRole('button', { name: '검색', exact: true }).click()
    await page.getByRole('button', { name: '새 배송 담당자 상세', exact: true }).click()
    await page.getByRole('checkbox', { name: '배송 담당자로 지정' }).uncheck()
    const release = page.waitForResponse(
      (r) => r.url().endsWith(`/users/${account.userId}/role`) && r.request().method() === 'PATCH',
    )
    await page.getByRole('button', { name: '배송 담당 설정 저장' }).click()
    const released = await release
    expect(released.status()).toBe(200)
    expect(JSON.parse(released.request().postData()!)).toEqual({ isDeliveryManager: false })
    await expect(page.getByRole('dialog').getByText('미지정', { exact: true })).toBeVisible()
  } finally {
    await cleanup(request, resources)
  }
})

test('모바일 업체 등록·담당자 선택의 레이아웃과 접근성', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/companies?create=1')
  await login(page, 'uimaster')
  await expect(page.getByRole('dialog', { name: '업체 등록' })).toBeVisible()
  await page
    .getByRole('dialog')
    .evaluate((el) => Promise.all(el.getAnimations({ subtree: true }).map((a) => a.finished)))
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
  expect(audit.violations.map((v) => ({ id: v.id, nodes: v.nodes.map((n) => n.target) }))).toEqual(
    [],
  )
  await page.screenshot({ path: testInfo.outputPath('company-form-mobile.png') })
})

test('이메일만으로 가입, MASTER 수정·해제와 USER 편집 제한', async ({
  page,
  request,
}, testInfo) => {
  const resources = { orders: [] as string[], companies: [] as string[], users: [] as string[] }
  const username = unique('ue')
  try {
    await page.goto('/signup')
    await page.getByLabel('아이디', { exact: true }).fill(username)
    await page.getByLabel('비밀번호', { exact: true }).fill(password)
    await page.getByLabel('비밀번호 확인', { exact: true }).fill(password)
    await page.getByLabel('이름', { exact: true }).fill('이메일 검증 담당자')
    await expect(page.getByLabel('Slack 멤버 ID', { exact: true })).toHaveCount(0)
    await page.getByLabel('소속명', { exact: true }).fill('DM 테스트')
    await page.getByLabel('Slack 계정 이메일', { exact: true }).fill('driver+signup@example.com')
    const registration = page.waitForResponse(
      (r) => r.url().endsWith('/auth/signup') && r.request().method() === 'POST',
    )
    await page.getByRole('button', { name: '계정 만들기' }).click()
    const result = await registration
    expect(result.status()).toBe(201)
    expect(result.request().postDataJSON()).not.toHaveProperty('slackId')
    expect(result.request().postDataJSON().slackEmail).toBe('driver+signup@example.com')
    const account = (await result.json()).data
    resources.users.push(account.userId)
    await login(page, username)
    await nav(page, '사용자 관리')
    await expect(page.getByText('driver+signup@example.com', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '알림 이메일 저장' })).toHaveCount(0)
    await page.getByRole('button', { name: '로그아웃' }).click()
    await login(page, 'uimaster')
    await nav(page, '사용자 관리')
    await page.getByRole('searchbox').fill('이메일 검증 담당자')
    await page.getByRole('button', { name: '검색', exact: true }).click()
    await page.getByRole('button', { name: '이메일 검증 담당자 상세', exact: true }).click()
    await page.getByLabel('Slack 계정 이메일', { exact: true }).fill('driver+updated@example.com')
    await page.getByRole('button', { name: '알림 이메일 저장' }).click()
    await expect(
      page
        .getByRole('dialog')
        .getByRole('definition')
        .filter({ hasText: 'driver+updated@example.com' }),
    ).toBeVisible()
    await page.screenshot({
      path: testInfo.outputPath('slack-email-settings.png'),
      fullPage: false,
    })
    await page.getByLabel('Slack 계정 이메일', { exact: true }).fill('')
    await page.getByRole('button', { name: '알림 이메일 저장' }).click()
    await expect(page.getByRole('dialog').getByText('미등록', { exact: true })).toBeVisible()
    const headers = await adminHeaders(request)
    const internal = await request.get(
      `http://127.0.0.1:18080/internal/users/${account.userId}/notification-contact`,
      { headers },
    )
    expect([403, 404]).toContain(internal.status())
  } finally {
    await cleanup(request, resources)
  }
})
