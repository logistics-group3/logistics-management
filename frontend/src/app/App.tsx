import { useState } from 'react'
import {
  BrowserRouter,
  Link,
  NavLink,
  Navigate,
  Route,
  Routes,
  useLocation,
} from 'react-router-dom'
import {
  ArrowUpRight,
  Boxes,
  Building2,
  ChevronRight,
  ClipboardCheck,
  Command,
  Container,
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
import { TestGuide } from '../features/TestGuide'
import { localDate } from '../shared/format'
const navigation = [
  { path: '/', label: '대시보드', icon: LayoutDashboard },
  { path: '/orders', label: '주문 관리', icon: Package },
  { path: '/deliveries', label: '배송 관리', icon: Truck },
  { path: '/companies', label: '업체 관리', icon: Building2 },
  { path: '/products', label: '상품 관리', icon: Boxes },
  { path: '/hubs', label: '허브 관리', icon: Network },
  { path: '/users', label: '사용자 관리', icon: Users },
]
// 로그인 전에도 가이드를 먼저 읽을 수 있게 사이드바 없는 화면으로 연다.
function PublicGuide() {
  return (
    <div className="public-guide">
      <header className="public-guide-bar">
        <Link className="brand" to="/">
          <span className="brand-symbol">
            <Container size={23} strokeWidth={1.7} />
          </span>
          <span>
            SPARTA<span>LOGISTICS</span>
          </span>
        </Link>
        <Link className="text-link" to="/">
          로그인 화면으로
        </Link>
      </header>
      <main id="main-content" className="main-content">
        <TestGuide />
      </main>
    </div>
  )
}
function Shell() {
  const { user, logout } = useAuth()
  const [menu, setMenu] = useState(false)
  const location = useLocation()
  if (!user) {
    if (location.pathname === '/signup') return <Signup />
    return location.pathname === '/guide' ? <PublicGuide /> : <Login />
  }
  const current = navigation.find((item) =>
    item.path === '/' ? location.pathname === '/' : location.pathname.startsWith(item.path),
  )
  const currentLabel = location.pathname.startsWith('/guide')
    ? '시나리오 테스트 가이드'
    : current?.label
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
          <span className="brand-symbol">
            <Container size={23} strokeWidth={1.7} />
          </span>
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
          <span className="workspace-avatar">
            <Command size={16} />
          </span>
          <div>
            <strong>물류 관리 시스템</strong>
            <small>테스트 환경</small>
          </div>
        </div>
        <span className="nav-section-label">운영 관리</span>
        <nav aria-label="주 메뉴">
          {navigation.map(({ path, label, icon: Icon }, i) => (
            <div key={path}>
              {i === 3 && <span className="nav-section-label secondary-label">기준 정보</span>}
              <NavLink to={path} end={path === '/'} onClick={() => setMenu(false)}>
                <Icon size={19} />
                <span>{label}</span>
                <ChevronRight className="nav-chevron" size={14} />
              </NavLink>
            </div>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="environment-card">
            <span className="connection-dot" />
            <strong>테스트 환경</strong>
          </div>
          <NavLink className="sidebar-help" to="/guide" onClick={() => setMenu(false)}>
            <ClipboardCheck size={18} />
            시나리오 테스트 가이드
            <ChevronRight size={15} />
          </NavLink>
          <span className="sidebar-version">물류 관리 시스템</span>
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
            <span className="breadcrumb-home">통합 물류 관리</span>
            <ChevronRight size={14} />
            <strong>{currentLabel ?? '화면'}</strong>
          </div>
          <div className="topbar-account">
            <span className="topbar-date">{localDate().replaceAll('-', '.')}</span>
            <Link className="topbar-guide" to="/guide">
              운영 가이드 <ArrowUpRight size={13} />
            </Link>
            <span className="environment-pill">TEST</span>
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
            <Route path="/guide" element={<TestGuide />} />
            {(['companies', 'products', 'hubs', 'users'] as const).map((kind) => (
              <Route key={kind} path={`/${kind}`} element={<Catalog key={kind} kind={kind} />} />
            ))}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
        <footer className="app-footer">
          <span>SPARTA LOGISTICS</span>
          <span>
            통합 물류 운영 시스템 <span className="footer-separator">/</span> 전용 테스트 환경
          </span>
        </footer>
      </div>
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
