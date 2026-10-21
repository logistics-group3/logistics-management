import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  CheckCircle2,
  Circle,
  ExternalLink,
  KeyRound,
  LoaderCircle,
  LogIn,
  RotateCcw,
  TriangleAlert,
} from 'lucide-react'
import { useAuth } from '../app/auth'
import { Button } from '../components/ui/button'
import { ErrorNotice, PageTitle } from '../shared/ui'
import {
  TEST_PASSWORD,
  knownGaps,
  scenarios,
  testAccounts,
  testLinks,
  type AccountName,
  type Scenario,
} from '../shared/test-guide'

const PROGRESS_KEY = 'sparta-logistics-mentor-guide-v1'

// Note: 확인 표시는 보는 사람 브라우저에만 남기는 편의 기능이다. 저장소를 못 쓰는 환경에서도 화면은 그대로 동작한다.
function loadProgress(): string[] {
  try {
    const value = JSON.parse(localStorage.getItem(PROGRESS_KEY) ?? '[]')
    return Array.isArray(value) ? value.filter((item) => typeof item === 'string') : []
  } catch {
    return []
  }
}

function saveProgress(ids: string[]) {
  try {
    localStorage.setItem(PROGRESS_KEY, JSON.stringify(ids))
  } catch {
    // 저장하지 못해도 이번 화면의 표시는 유지한다.
  }
}

export function TestGuide() {
  const { user, login, logout } = useAuth()
  const navigate = useNavigate()
  const [done, setDone] = useState<string[]>(loadProgress)
  const [pending, setPending] = useState<string>()
  const [error, setError] = useState<unknown>()
  const areas = useMemo(() => [...new Set(scenarios.map((s) => s.area))], [])

  const toggle = (id: string) => {
    const next = done.includes(id) ? done.filter((item) => item !== id) : [...done, id]
    setDone(next)
    saveProgress(next)
  }

  const start = async (username: AccountName, path = '/', key: string = username) => {
    if (pending) return
    setPending(key)
    setError(undefined)
    try {
      if (user?.username !== username) {
        if (user) logout()
        await login(username, TEST_PASSWORD)
      }
      navigate(path)
    } catch (value) {
      setError(value)
    } finally {
      setPending(undefined)
    }
  }

  return (
    <div className="test-guide">
      <PageTitle
        title="멘토 테스트 가이드"
        description="계정 선택 시 해당 계정으로 로그인하고 시나리오 화면으로 이동합니다."
        actions={
          <div className="guide-progress" aria-live="polite">
            <strong>
              {done.length} / {scenarios.length}
            </strong>
            <span>확인 완료</span>
            {done.length > 0 && (
              <Button
                variant="ghost"
                onClick={() => {
                  setDone([])
                  saveProgress([])
                }}
              >
                <RotateCcw size={14} />
                기록 초기화
              </Button>
            )}
          </div>
        }
      />
      <ErrorNotice
        error={error}
        help="테스트 서버와 시드 데이터(script/ui-test.sh seed)를 확인하세요."
      />

      <section className="panel guide-section" aria-labelledby="guide-env">
        <div className="panel-heading">
          <div>
            <h2 id="guide-env">테스트 환경</h2>
            <p>
              script/ui-test.sh up → seed → frontend 순서로 띄운 전용 환경입니다. 개발 DB와 분리돼
              있고 Slack은 꺼져 있습니다.
            </p>
          </div>
        </div>
        <div className="guide-links">
          {testLinks.map((link) => (
            <a key={link.label} href={link.url} target="_blank" rel="noreferrer">
              <strong>
                {link.label}
                <ExternalLink size={13} aria-hidden />
              </strong>
              <span>{link.description}</span>
            </a>
          ))}
        </div>
      </section>

      <section className="panel guide-section" aria-labelledby="guide-accounts">
        <div className="panel-heading">
          <div>
            <h2 id="guide-accounts">테스트 계정</h2>
            <p>
              <KeyRound size={13} aria-hidden /> 공통 비밀번호 <code>{TEST_PASSWORD}</code> · 전용
              테스트 DB에만 있는 공개 예제 계정입니다.
            </p>
          </div>
        </div>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>아이디</th>
                <th>이름 · 역할</th>
                <th>확인할 내용</th>
                <th>
                  <span className="sr-only">로그인</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {testAccounts.map((account) => (
                <tr key={account.username}>
                  <td>
                    <code>{account.username}</code>
                    {user?.username === account.username && (
                      <span className="guide-current">현재 계정</span>
                    )}
                  </td>
                  <td>
                    <strong>{account.name}</strong>
                    <small>{account.role}</small>
                  </td>
                  <td>{account.checks}</td>
                  <td className="guide-action">
                    <Button
                      variant="secondary"
                      disabled={Boolean(pending)}
                      aria-label={`${account.username} 계정으로 시작`}
                      onClick={() => void start(account.username)}
                    >
                      {pending === account.username ? (
                        <LoaderCircle className="spin" size={14} />
                      ) : (
                        <LogIn size={14} />
                      )}
                      시작
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="guide-section" aria-labelledby="guide-scenarios">
        <h2 id="guide-scenarios" className="guide-heading">
          발제 필수 기능 시나리오
        </h2>
        {areas.map((area) => (
          <div key={area} className="guide-area">
            <h3>{area}</h3>
            <div className="guide-cards">
              {scenarios
                .filter((s) => s.area === area)
                .map((scenario) => (
                  <ScenarioCard
                    key={scenario.id}
                    scenario={scenario}
                    checked={done.includes(scenario.id)}
                    busy={pending === scenario.id}
                    disabled={Boolean(pending)}
                    onToggle={() => toggle(scenario.id)}
                    onStart={() =>
                      scenario.account && void start(scenario.account, scenario.path, scenario.id)
                    }
                  />
                ))}
            </div>
          </div>
        ))}
      </section>

      <section className="panel guide-section" aria-labelledby="guide-gaps">
        <div className="panel-heading">
          <div>
            <h2 id="guide-gaps">현재 범위와 발제와 다른 점</h2>
            <p>시나리오의 기대 결과는 실제 서버 동작 기준입니다.</p>
          </div>
        </div>
        <ul className="guide-gaps">
          {knownGaps.map((gap) => (
            <li key={gap}>
              <TriangleAlert size={15} aria-hidden />
              {gap}
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}

function ScenarioCard({
  scenario,
  checked,
  busy,
  disabled,
  onToggle,
  onStart,
}: {
  scenario: Scenario
  checked: boolean
  busy: boolean
  disabled: boolean
  onToggle: () => void
  onStart: () => void
}) {
  const titleId = `scenario-${scenario.id}`
  return (
    <article className={`guide-card ${checked ? 'guide-card-done' : ''}`} aria-labelledby={titleId}>
      <header>
        <button
          className="guide-check"
          role="checkbox"
          aria-checked={checked}
          aria-label={`${scenario.title} 확인 완료`}
          onClick={onToggle}
        >
          {checked ? <CheckCircle2 size={20} /> : <Circle size={20} />}
        </button>
        <div>
          <h4 id={titleId}>{scenario.title}</h4>
          <p className="guide-requirement">발제: {scenario.requirement}</p>
        </div>
        {scenario.limited && <span className="guide-limited">구현 대기</span>}
      </header>
      <ol>
        {scenario.steps.map((step) => (
          <li key={step}>{step}</li>
        ))}
      </ol>
      <div className="guide-expected">
        <strong>기대 결과</strong>
        <p>{scenario.expected}</p>
      </div>
      <footer>
        {scenario.account ? (
          <Button variant="secondary" disabled={disabled} onClick={onStart}>
            {busy ? <LoaderCircle className="spin" size={14} /> : <LogIn size={14} />}
            {scenario.account}로 시작
          </Button>
        ) : (
          <span className="guide-hint">
            {scenario.hint ?? '위 테스트 환경의 Swagger·Zipkin·Grafana에서 진행합니다.'}
          </span>
        )}
      </footer>
    </article>
  )
}
