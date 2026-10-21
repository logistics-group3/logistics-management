import { useState } from 'react'
import { ArrowRight, LoaderCircle, ShieldCheck } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { AuthLayout } from './AuthLayout'
import { useAuth } from './auth'
import { Button } from '../components/ui/button'
import { ErrorNotice, Field } from '../shared/ui'
import { TEST_PASSWORD, testAccounts } from '../shared/test-guide'
export function Login() {
  const auth = useAuth()
  const location = useLocation()
  const registeredUsername = (location.state as { registeredUsername?: string } | null)
    ?.registeredUsername
  const [username, setUsername] = useState(registeredUsername ?? '')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  return (
    <AuthLayout>
      <span className="login-icon">
        <ShieldCheck size={25} />
      </span>
      <h2>운영 콘솔 로그인</h2>
      {registeredUsername && (
        <div className="notice signup-success" role="status">
          회원가입이 완료되었습니다. {registeredUsername} 계정으로 로그인해 주세요.
        </div>
      )}
      {auth.expired && (
        <div className="notice">
          세션이 만료되었습니다. 다시 로그인해 주세요. 미완료 주문 요청은 이 탭에 보관되어 있습니다.
        </div>
      )}
      <form
        onSubmit={async (event) => {
          event.preventDefault()
          if (busy) return
          setBusy(true)
          setError(undefined)
          try {
            await auth.login(username, password)
          } catch (value) {
            setError(value)
          } finally {
            setBusy(false)
          }
        }}
      >
        <Field label="아이디">
          <input
            name="username"
            autoComplete="username"
            placeholder="아이디를 입력하세요"
            required
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </Field>
        <Field label="비밀번호">
          <input
            name="password"
            type="password"
            autoComplete="current-password"
            placeholder="비밀번호를 입력하세요"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        <ErrorNotice error={error} />
        <Button className="login-submit" disabled={busy}>
          {busy ? (
            <LoaderCircle className="spin" size={18} />
          ) : (
            <>
              로그인
              <ArrowRight size={18} />
            </>
          )}
        </Button>
      </form>
      <details className="login-accounts">
        <summary>테스트 계정 입력 (공통 비밀번호 {TEST_PASSWORD})</summary>
        <div>
          {testAccounts.map((account) => (
            <button
              key={account.username}
              type="button"
              aria-label={`${account.username} 계정 입력`}
              onClick={() => {
                setUsername(account.username)
                setPassword(TEST_PASSWORD)
              }}
            >
              <code>{account.username}</code>
              <span>{account.role}</span>
            </button>
          ))}
        </div>
      </details>
      <div className="auth-switch">
        <Link to="/signup">
          회원가입
          <ArrowRight size={14} />
        </Link>
      </div>
      <div className="login-footnote">
        <span className="connection-dot" />
        전용 테스트 환경
      </div>
      <p className="login-help">
        <Link to="/guide">시나리오 테스트 가이드</Link>
        <br />
        새로고침 후에는 다시 로그인이 필요합니다.
      </p>
    </AuthLayout>
  )
}
