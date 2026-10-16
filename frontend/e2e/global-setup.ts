import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
export default async function globalSetup() {
  // Note: 자동 테스트 주문이 실제 Slack으로 발송되지 않게 서버 설정을 먼저 확인한다. 토큰 값은 출력하지 않는다.
  const containers = JSON.parse(
    execFileSync('docker', ['inspect', 'logistics-ui-test-order-service-1'], { encoding: 'utf8' }),
  )
  const environment: string[] = containers[0].Config.Env
  if (
    environment.some(
      (entry) =>
        entry.startsWith('SLACK_DM_ENABLED=') &&
        ['true', '1', 'yes', 'on'].includes(entry.split('=')[1].trim().toLowerCase()),
    )
  )
    throw new Error(
      '실제 DM 활성화 상태에서는 E2E를 실행하지 않습니다. UI 테스트 서버의 DM을 먼저 비활성화하세요.',
    )
  if (
    environment.some(
      (entry) => entry.startsWith('SLACK_WEBHOOK_URL=') && entry !== 'SLACK_WEBHOOK_URL=',
    )
  )
    throw new Error('실제 Webhook 활성화 상태에서는 E2E를 실행하지 않습니다.')
  const fixture = JSON.parse(readFileSync('../.local/ui-test-data.json', 'utf8'))
  const response = await fetch('http://127.0.0.1:5174/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'uimaster', password: 'UiLocal!2026' }),
  })
  if (!response.ok) throw new Error('UI 테스트 서버의 로그인을 확인할 수 없습니다.')
  const token = (await response.json()).data.accessToken
  const me = await fetch('http://127.0.0.1:5174/api/v1/users/me', {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!me.ok || (await me.json()).data.userId !== fixture.users.uimaster)
    throw new Error('프록시 대상이 전용 테스트 DB의 계정과 일치하지 않습니다. 테스트를 중단합니다.')
}
