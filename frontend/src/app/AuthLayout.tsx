import type { ReactNode } from 'react'
import { Container } from 'lucide-react'
export function AuthLayout({
  children,
  signup = false,
}: {
  children: ReactNode
  signup?: boolean
}) {
  return (
    <main className="login-page">
      <section className="login-story">
        <a className="brand" href="/">
          <span className="brand-symbol">
            <Container size={23} strokeWidth={1.7} />
          </span>
          <span>
            SPARTA<span>LOGISTICS</span>
          </span>
        </a>
        <div className="login-copy">
          <h1>물류 관리 시스템</h1>
        </div>
        <footer>SPARTA LOGISTICS</footer>
      </section>
      <section className="login-form-panel">
        <div className={`login-form ${signup ? 'signup-form' : ''}`}>{children}</div>
        <footer>SPARTA LOGISTICS · 통합 물류 운영 시스템</footer>
      </section>
    </main>
  )
}
