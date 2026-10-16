import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

const audit = async (page: import('@playwright/test').Page) => {
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
