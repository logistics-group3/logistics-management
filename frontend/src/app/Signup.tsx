import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowLeft, ArrowRight, LoaderCircle, UserRoundPlus } from 'lucide-react'
import { AuthLayout } from './AuthLayout'
import { Button } from '../components/ui/button'
import { ErrorNotice, Field } from '../shared/ui'
import { api, ApiError } from '../shared/api'
import { validateSignup } from '../shared/signup'
import type { SignupInput } from '../shared/signup'
const emptyForm: SignupInput = {
  username: '',
  password: '',
  passwordConfirm: '',
  name: '',
  slackEmail: '',
  affiliationType: 'COMPANY',
  affiliationName: '',
}
export function Signup() {
  const [form, setForm] = useState<SignupInput>({ ...emptyForm })
  const [validation, setValidation] = useState<Partial<Record<keyof SignupInput, string>>>({})
  const [error, setError] = useState<unknown>()
  const [busy, setBusy] = useState(false)
  const submitting = useRef(false)
  const navigate = useNavigate()
  const change = (key: keyof SignupInput, value: string) => {
    setForm((previous) => ({ ...previous, [key]: value }))
    setValidation((previous) => ({ ...previous, [key]: undefined }))
  }
  const uncertain = error instanceof ApiError && (error.status === 0 || error.status >= 500)
  return (
    <AuthLayout signup>
      <Link className="auth-back" to="/login">
        <ArrowLeft size={15} />
        로그인으로 돌아가기
      </Link>
      <span className="login-icon">
        <UserRoundPlus size={24} />
      </span>
      <span className="eyebrow">JOIN THE WORKSPACE</span>
      <h2>회원가입</h2>
      <p>계정을 만들고 물류 운영 워크스페이스에 참여하세요.</p>
      <form
        noValidate
        onSubmit={async (event) => {
          event.preventDefault()
          if (submitting.current) return
          const errors = validateSignup(form)
          setValidation(errors)
          setError(undefined)
          const first = Object.keys(errors)[0]
          if (first) {
            ;(event.currentTarget.elements.namedItem(first) as HTMLElement | null)?.focus()
            return
          }
          submitting.current = true
          setBusy(true)
          try {
            const { passwordConfirm: _confirmation, ...body } = form
            const result = await api<{ userId: string; username: string }>('/auth/signup', {
              method: 'POST',
              body: { ...body, slackEmail: body.slackEmail.trim() },
            })
            if (!result?.userId)
              throw new ApiError(0, 'INVALID_RESPONSE', '가입 결과를 확인할 수 없습니다.')
            setForm({ ...emptyForm })
            navigate('/login', { replace: true, state: { registeredUsername: result.username } })
          } catch (value) {
            setError(value)
          } finally {
            submitting.current = false
            setBusy(false)
          }
        }}
      >
        <fieldset disabled={busy}>
          <h3 className="signup-section-title">계정 정보</h3>
          <Field label="아이디" hint={validation.username ?? '영문 소문자·숫자, 4~10자'}>
            <input
              name="username"
              autoComplete="username"
              maxLength={10}
              required
              value={form.username}
              aria-invalid={Boolean(validation.username)}
              onChange={(e) => change('username', e.target.value)}
              placeholder="예: logistics1"
            />
          </Field>
          <div className="form-grid">
            <Field
              label="비밀번호"
              hint={validation.password ?? '8~15자 · 대문자·소문자·숫자·특수문자'}
            >
              <input
                name="password"
                type="password"
                autoComplete="new-password"
                maxLength={15}
                required
                aria-invalid={Boolean(validation.password)}
                value={form.password}
                onChange={(e) => change('password', e.target.value)}
              />
            </Field>
            <Field
              label="비밀번호 확인"
              hint={validation.passwordConfirm ?? '동일한 비밀번호를 한 번 더 입력하세요.'}
            >
              <input
                name="passwordConfirm"
                type="password"
                autoComplete="new-password"
                maxLength={15}
                required
                aria-invalid={Boolean(validation.passwordConfirm)}
                value={form.passwordConfirm}
                onChange={(e) => change('passwordConfirm', e.target.value)}
              />
            </Field>
          </div>
          <h3 className="signup-section-title">담당자 및 소속 정보</h3>
          <div className="form-grid">
            <Field label="이름" hint={validation.name}>
              <input
                name="name"
                autoComplete="name"
                maxLength={50}
                required
                value={form.name}
                aria-invalid={Boolean(validation.name)}
                onChange={(e) => change('name', e.target.value)}
                placeholder="담당자 이름"
              />
            </Field>
            <Field
              label="Slack 계정 이메일"
              hint={
                validation.slackEmail ??
                '배송담당자로 배정될 때 DM을 받을 Slack 워크스페이스 계정 이메일입니다.'
              }
            >
              <input
                name="slackEmail"
                type="email"
                required
                autoComplete="email"
                maxLength={254}
                value={form.slackEmail}
                aria-invalid={Boolean(validation.slackEmail)}
                onChange={(e) => change('slackEmail', e.target.value)}
                placeholder="driver@example.com"
              />
            </Field>
          </div>
          <div className="form-grid">
            <Field label="소속 유형">
              <select
                name="affiliationType"
                value={form.affiliationType}
                onChange={(e) => change('affiliationType', e.target.value)}
              >
                <option value="COMPANY">업체</option>
                <option value="HUB">허브</option>
              </select>
            </Field>
            <Field label="소속명" hint={validation.affiliationName}>
              <input
                name="affiliationName"
                maxLength={100}
                required
                value={form.affiliationName}
                aria-invalid={Boolean(validation.affiliationName)}
                onChange={(e) => change('affiliationName', e.target.value)}
                placeholder="소속 업체 또는 허브 이름"
              />
            </Field>
          </div>
        </fieldset>
        {Object.values(validation).some(Boolean) && (
          <div role="alert" className="notice error">
            입력 항목을 확인해 주세요. {Object.values(validation).filter(Boolean)[0]}
          </div>
        )}
        <ErrorNotice
          error={error}
          help={
            uncertain
              ? '가입이 완료되었을 수도 있습니다. 방금 입력한 아이디로 먼저 로그인을 확인해 주세요. 가입 요청은 자동 재전송하지 않습니다.'
              : undefined
          }
        />
        <div className="signup-role-note">
          일반 사용자로 가입됩니다. 가입 후 업체를 등록하면 본인이 담당자로 연결됩니다. 다른
          사용자의 업체 담당자 변경과 배송 담당자 지정은 관리자가 처리합니다.
        </div>
        <Button className="login-submit" disabled={busy}>
          {busy ? (
            <>
              <LoaderCircle className="spin" size={17} />
              가입 처리 중…
            </>
          ) : (
            <>
              계정 만들기
              <ArrowRight size={17} />
            </>
          )}
        </Button>
      </form>
      <div className="auth-switch">
        <span>이미 계정이 있으신가요?</span>
        <Link to="/login">로그인</Link>
      </div>
    </AuthLayout>
  )
}
