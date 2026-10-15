import { useState } from 'react'
import { BrowserRouter, NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import {
  Boxes,
  Building2,
  ChevronRight,
  CircleHelp,
  LayoutDashboard,
  LogOut,
  Menu,
  Network,
  Package,
  Truck,
  Users,
  X,
} from 'lucide-react'
import { Providers } from './context'
import { useAuth } from './auth'
import { Login } from './Login'
import { Signup } from './Signup'
import { Dashboard } from '../features/Dashboard'
import { Orders } from '../features/Orders'
import { Deliveries } from '../features/Deliveries'
import { Catalog } from '../features/Catalog'
import { Dialog } from '../components/ui/dialog'
import { Button } from '../components/ui/button'
const navigation = [
  { path: '/', label: '대시보드', icon: LayoutDashboard },
  { path: '/orders', label: '주문 관리', icon: Package },
  { path: '/deliveries', label: '배송 관리', icon: Truck },
  { path: '/companies', label: '업체 관리', icon: Building2 },
  { path: '/products', label: '상품 관리', icon: Boxes },
  { path: '/hubs', label: '허브 관리', icon: Network },
  { path: '/users', label: '사용자 관리', icon: Users },
]
function Shell() {
  const { user, logout } = useAuth()
  const [menu, setMenu] = useState(false)
  const [help, setHelp] = useState(false)
  const location = useLocation()
  if (!user) return location.pathname === '/signup' ? <Signup /> : <Login />
  const current = navigation.find((item) =>
    item.path === '/' ? location.pathname === '/' : location.pathname.startsWith(item.path),
  )
  return (
    <div className="app-shell">
      <a href="#main-content" className="skip-link">
        본문으로 이동
      </a>
      {menu && (
        <button
          aria-label="메뉴 배경 닫기"
          className="nav-backdrop"
          onClick={() => setMenu(false)}
        />
      )}
      <aside className={`sidebar ${menu ? 'sidebar-open' : ''}`}>
        <NavLink className="brand" to="/">
          <span className="brand-symbol">S</span>
          <span>
            SPARTA<span>LOGISTICS</span>
          </span>
        </NavLink>
        <button
          className="mobile-close icon-button"
          aria-label="메뉴 닫기"
          onClick={() => setMenu(false)}
        >
          <X size={20} />
        </button>
        <div className="workspace-tag">
          <span className="workspace-avatar">SL</span>
          <div>
            <strong>물류 운영 워크스페이스</strong>
            <small>LOCAL WORKSPACE</small>
          </div>
        </div>
        <span className="nav-section-label">WORKSPACE</span>
        <nav aria-label="주 메뉴">
          {navigation.map(({ path, label, icon: Icon }, i) => (
            <div key={path}>
              {i === 3 && <span className="nav-section-label secondary-label">MASTER DATA</span>}
              <NavLink to={path} end={path === '/'} onClick={() => setMenu(false)}>
                <Icon size={19} />
                <span>{label}</span>
                {path === '/orders' && <span className="nav-indicator" />}
              </NavLink>
            </div>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="environment-card">
            <span className="connection-dot" />
            <strong>테스트 워크스페이스</strong>
            <p>주문부터 배송까지 직접 확인하세요.</p>
          </div>
          <button className="sidebar-help" onClick={() => setHelp(true)}>
            <CircleHelp size={18} />
            테스트 이용 안내
            <ChevronRight size={15} />
          </button>
          <span className="sidebar-version">SPARTA LOGISTICS / CONSOLE 1.0</span>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="icon-button mobile-toggle"
              aria-label="메뉴 열기"
              onClick={() => setMenu(true)}
            >
              <Menu size={20} />
            </button>
            <span>워크스페이스</span>
            <ChevronRight size={14} />
            <strong>{current?.label ?? '화면'}</strong>
          </div>
          <div className="topbar-account">
            <span className="environment-pill">LOCAL</span>
            <div className="account-avatar">{user.name.slice(0, 1)}</div>
            <div className="account-info">
              <strong>{user.name}</strong>
              <span>
                {user.role === 'MASTER'
                  ? '시스템 관리자'
                  : user.isDeliveryManager
                    ? '배송 담당자'
                    : user.affiliationName}
              </span>
            </div>
            <button className="icon-button" aria-label="로그아웃" title="로그아웃" onClick={logout}>
              <LogOut size={18} />
            </button>
          </div>
        </header>
        <main id="main-content" className="main-content">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/orders/*" element={<Orders />} />
            <Route path="/deliveries/*" element={<Deliveries />} />
            {(['companies', 'products', 'hubs', 'users'] as const).map((kind) => (
              <Route key={kind} path={`/${kind}`} element={<Catalog key={kind} kind={kind} />} />
            ))}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
        <footer className="app-footer">
          <span>SPARTA LOGISTICS</span>
          <span>물류 운영 콘솔 · 테스트 데이터</span>
        </footer>
      </div>
      <Dialog
        open={help}
        onClose={() => setHelp(false)}
        title="테스트 이용 안내"
        description="실제 API를 사용하는 독립된 테스트 환경입니다."
      >
        <ol className="help-steps">
          <li>
            회원가입 후 업체 관리에서 내 업체를 등록하면 본인이 담당자로 연결됩니다. 새로운
            공급업체를 만들었다면 상품 관리에서 상품을 등록합니다.
          </li>
          <li>
            관리자는 업체 상세에서 담당자를 변경하고, 사용자 상세에서 배송 담당자와 담당 허브를
            지정할 수 있습니다.
          </li>
          <li>
            주문 관리에서 새 주문을 등록합니다. 준비된 업체들은 같은 허브에 소속되어 있습니다.
          </li>
          <li>주문 상세에서 요청사항 수정, 집하 전 취소를 테스트할 수 있습니다.</li>
          <li>배송 상세에서 집하 출발 → 도착 → 업체 배송 출발 → 도착 순으로 진행합니다.</li>
          <li>관리자는 취소된 주문 또는 배송이 완료된 주문을 삭제할 수 있습니다.</li>
        </ol>
        <div className="notice">
          다른 허브 간 배송은 허브 경로 API가 준비된 뒤 테스트할 수 있습니다. Slack 알림은 이 테스트
          서버에서 꺼져 있습니다.
        </div>
        <Button onClick={() => setHelp(false)}>확인</Button>
      </Dialog>
    </div>
  )
}
export default function App() {
  return (
    <Providers>
      <BrowserRouter>
        <Shell />
      </BrowserRouter>
    </Providers>
  )
}
