import type { ReactNode } from 'react'
import { Box, Check, Layers, Truck } from 'lucide-react'
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
          <span className="brand-symbol">S</span>
          <span>
            SPARTA<span>LOGISTICS</span>
          </span>
        </a>
        <div className="login-copy">
          <span className="eyebrow">CONNECTED LOGISTICS</span>
          <h1>
            모든 움직임을,
            <br />
            하나의 흐름으로.
          </h1>
          <p>
            주문 접수부터 마지막 배송까지.
            <br />
            물류 운영의 모든 순간을 연결합니다.
          </p>
          <div className="login-route">
            <div>
              <Box />
              <span>주문 접수</span>
              <Check size={14} />
            </div>
            <i />
            <div>
              <Layers />
              <span>허브 연결</span>
              <Check size={14} />
            </div>
            <i />
            <div>
              <Truck />
              <span>배송 완료</span>
              <Check size={14} />
            </div>
          </div>
        </div>
        <footer>SPARTA LOGISTICS · OPERATIONS PLATFORM</footer>
      </section>
      <section className="login-form-panel">
        <div className={`login-form ${signup ? 'signup-form' : ''}`}>{children}</div>
        <footer>물류의 시작과 끝, 더 명확하게.</footer>
      </section>
    </main>
  )
}
