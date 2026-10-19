import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Mail, Save } from 'lucide-react'
import { useAuth } from '../app/auth'
import { api, ApiError } from '../shared/api'
import type { User } from '../shared/types'
import { ErrorNotice, Field } from '../shared/ui'
import { Button } from '../components/ui/button'
export function SlackEmailForm({ target }: { target: User }) {
  const { user, notify, refreshUser } = useAuth()
  const [email, setEmail] = useState(target.slackEmail ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const pending = useRef(false)
  const cache = useQueryClient()
  if (user?.role !== 'MASTER') return null
  return (
    <section className="assignment-section">
      <h3>
        <Mail size={18} />
        Slack DM 알림
      </h3>
      <p className="muted small">
        배송담당자가 가입한 Slack 워크스페이스의 이메일을 등록합니다. 주문 생성 후 담당 구간의 배정
        알림을 보냅니다.
      </p>
      <form
        onSubmit={async (event) => {
          event.preventDefault()
          if (pending.current) return
          const value = email.trim()
          pending.current = true
          setBusy(true)
          setError(undefined)
          try {
            const result = await api<User>(`/users/${target.userId}`, {
              method: 'PATCH',
              body: { slackEmail: value },
            })
            if ((result.slackEmail ?? '') !== value)
              throw new ApiError(
                0,
                'EMAIL_NOT_APPLIED',
                '서버에 이메일 설정이 반영되지 않았습니다. 사용자 정보를 다시 확인해 주세요.',
              )
            if (target.userId === user.userId) await refreshUser()
            await cache.invalidateQueries({ queryKey: ['users'] })
            notify(
              value ? 'Slack 알림 이메일을 저장했습니다.' : 'Slack 알림 이메일을 해제했습니다.',
            )
          } catch (value) {
            setError(value)
          } finally {
            pending.current = false
            setBusy(false)
          }
        }}
      >
        <ErrorNotice error={error} />
        <Field
          label="Slack 계정 이메일"
          hint="같은 워크스페이스의 계정 이메일 · 비우고 저장하면 DM 알림을 해제합니다."
        >
          <input
            type="email"
            autoComplete="off"
            maxLength={254}
            value={email}
            disabled={busy}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="driver@example.com"
          />
        </Field>
        <div className="notice">
          이메일 조회에 성공해도 실제 발송에는 서버의 Slack 앱 설정이 필요합니다. 이메일 미설정이나
          Slack 장애가 주문 생성을 막지는 않습니다.
        </div>
        <div className="actions align-end">
          <Button disabled={busy || email.trim() === (target.slackEmail ?? '')}>
            <Save size={16} />
            {busy ? '저장 중…' : '알림 이메일 저장'}
          </Button>
        </div>
      </form>
    </section>
  )
}
