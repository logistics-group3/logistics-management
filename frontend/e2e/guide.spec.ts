import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import fs from 'node:fs'

const fixture = JSON.parse(fs.readFileSync('../.local/ui-test-data.json', 'utf8'))
const password = 'UiLocal!2026'

async function login(page: Page, username: string) {
  await page.getByLabel('아이디', { exact: true }).fill(username)
  await page.getByLabel('비밀번호', { exact: true }).fill(password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '운영 콘솔 로그인', exact: true })).toHaveCount(0)
}

// 토큰은 메모리에만 있으므로 주소를 다시 열면 로그인 화면이 된다. 열린 상세 다이얼로그와 무관하게 계정을 바꾼다.
async function switchTo(page: Page, username: string, url: string) {
  await page.goto(url)
  await login(page, username)
}

async function advance(page: Page, times: number) {
  for (let i = 0; i < times; i++) {
    const action = page.getByRole('button', { name: /^(출발|도착) 처리$/ }).first()
    await action.click()
    // 허브 간 이동 도착은 배송 경로 기록에 실제 거리·소요시간을 남겨야 한다
    const distance = page.getByLabel('실제 거리 (km)', { exact: true })
    if (await distance.isVisible()) {
      await distance.fill('450.5')
      await page.getByLabel('실제 소요 시간 (분)', { exact: true }).fill('395')
    }
    await page.getByRole('button', { name: '상태 변경 확인', exact: true }).click()
    await expect(page.getByRole('button', { name: '상태 변경 확인', exact: true })).toHaveCount(0)
  }
}

const audit = async (page: Page) => {
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze()
  expect(result.violations.map((v) => ({ id: v.id, nodes: v.nodes.map((n) => n.target) }))).toEqual(
    [],
  )
}

test('멘토 가이드: 로그인 전 열람 → 시나리오 계정으로 시작 → 계정 전환', async ({
  page,
}, testInfo) => {
  await page.goto('/guide')
  await expect(page.getByRole('heading', { name: '멘토 테스트 가이드', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '운영 콘솔 로그인' })).toHaveCount(0)
  await audit(page)

  const order = page.getByRole('article', { name: '주문하면 배송과 배송 경로 기록이 함께 생긴다' })
  await order.getByRole('button', { name: 'uiuser로 시작', exact: true }).click()
  await expect(page).toHaveURL(/\/orders\/new$/)
  await expect(page.getByRole('dialog', { name: '새 주문 등록' })).toBeVisible()
  await expect(page.locator('.account-info strong')).toHaveText('김서연')

  await page.keyboard.press('Escape')
  await page.getByRole('link', { name: '멘토 테스트 가이드' }).click()
  await expect(page.getByRole('heading', { name: '멘토 테스트 가이드', exact: true })).toBeVisible()
  const check = page.getByRole('checkbox', {
    name: '주문하면 배송과 배송 경로 기록이 함께 생긴다 확인 완료',
  })
  await check.click()
  await expect(check).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByText(/^1 \/ \d+$/)).toBeVisible()
  await audit(page)

  const users = page.getByRole('article', { name: '사용자 조회는 본인만, 관리자는 전체를 본다' })
  await users.getByRole('button', { name: 'uiuser로 시작', exact: true }).click()
  await expect(page).toHaveURL(/\/users$/)
  await page.getByRole('link', { name: '멘토 테스트 가이드' }).click()
  await page
    .getByRole('article', { name: '배송담당자는 별도 엔티티가 아니라 사용자 권한으로 지정한다' })
    .getByRole('button', { name: 'uimaster로 시작', exact: true })
    .click()
  await expect(page).toHaveURL(/\/users$/)
  await expect(page.locator('.account-info strong')).toHaveText('마스터')
  await page.screenshot({ path: testInfo.outputPath('guide-switched-account.png') })
})

test('로그인 화면의 테스트 계정 버튼은 입력만 채운다', async ({ page }) => {
  await page.goto('/')
  await page.getByText(/^테스트 계정 입력/).click()
  await page.getByRole('button', { name: 'uidriver 계정 입력', exact: true }).click()
  await expect(page.getByLabel('아이디', { exact: true })).toHaveValue('uidriver')
  await expect(page.getByRole('heading', { name: '운영 콘솔 로그인', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.locator('.account-info span')).toHaveText('배송 담당자')
})

test('발제 예시: 경기 → 부산 허브 간 주문, 출발 허브 담당자별 구간 처리, 완료 후 삭제', async ({
  page,
}, testInfo) => {
  const scenario = fixture.scenario
  await page.goto('/orders/new')
  await login(page, 'uimaster')
  const form = page.getByRole('dialog', { name: '새 주문 등록' })
  await form.getByLabel('공급업체', { exact: true }).selectOption(scenario['경기 건조식품 가공'])
  await form.getByLabel('수령업체', { exact: true }).selectOption(scenario['부산 수산 도매'])
  await expect(form.getByText(/다른 허브 간 주문입니다/)).toBeVisible()
  await form
    .getByLabel('상품', { exact: true })
    .selectOption(scenario.products['마른오징어 가공품'])
  await form.getByLabel('주문 수량', { exact: true }).fill('50')
  await form.getByRole('button', { name: '주문 등록', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '주문 상세' })).toBeVisible()
  const orderUrl = page.url()
  await page.getByRole('link', { name: '배송 상세', exact: true }).click()
  await expect(page.locator('.route-step')).toHaveCount(3)
  const transfer = page.locator('.route-step').nth(1)
  await expect(transfer.getByRole('heading', { name: '허브 간 이동' })).toBeVisible()
  await expect(transfer).toContainText('경기 북부 센터')
  await expect(transfer).toContainText('부산광역시 센터')
  await expect(transfer).toContainText(/예상 [\d.]+km · \d+분/)
  const deliveryUrl = page.url()

  // 집하와 허브 간 이동은 출발 허브(경기 북부) 담당자, 업체 배송은 도착 허브(부산) 담당자에게 배정된다
  await switchTo(page, 'uidrvgg', deliveryUrl)
  await advance(page, 4)
  await expect(page.getByRole('button', { name: /^(출발|도착) 처리$/ })).toHaveCount(0)
  await switchTo(page, 'uidrvbs', deliveryUrl)
  await advance(page, 2)
  await expect(page.locator('.detail-hero .badge')).toHaveText('배송 완료')
  await page.screenshot({ path: testInfo.outputPath('cross-hub-delivered.png'), fullPage: true })

  await switchTo(page, 'uimaster', orderUrl)
  await page.getByRole('button', { name: '주문 삭제', exact: true }).click()
  await page.getByRole('button', { name: '삭제 확인', exact: true }).click()
  await expect(page).toHaveURL(/\/orders$/)
})
