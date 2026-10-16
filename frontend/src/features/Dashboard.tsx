import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ArrowDownLeft, ArrowRight, CheckCheck, Package, Plus, Truck } from 'lucide-react'
import { api } from '../shared/api'
import { dateTime, localDate, money, shortId } from '../shared/format'
import { useDirectory } from '../shared/queries'
import { Badge, Empty, ErrorNotice, Loading, PageTitle } from '../shared/ui'
import { Button } from '../components/ui/button'
import type { Delivery, Order, Page } from '../shared/types'
import { useAuth } from '../app/auth'
import { SetupGuide } from './SetupGuide'
export function Dashboard() {
  const { user } = useAuth()
  const directory = useDirectory()
  const today = localDate()
  const orders = useQuery({
    queryKey: ['orders', 'recent'],
    queryFn: ({ signal }) => api<Page<Order>>('/orders?size=10&sort=createdAt,desc', { signal }),
  })
  const deliveries = useQuery({
    queryKey: ['deliveries', 'recent'],
    queryFn: ({ signal }) =>
      api<Page<Delivery>>('/deliveries?size=10&sort=createdAt,desc', { signal }),
  })
  const todayOrders = useQuery({
    queryKey: ['orders', 'count', today],
    queryFn: ({ signal }) =>
      api<Page<Order>>(`/orders?size=10&startDate=${today}&endDate=${today}`, { signal }),
  })
  const waiting = useQuery({
    queryKey: ['deliveries', 'count', 'PICKUP_WAITING'],
    queryFn: ({ signal }) =>
      api<Page<Delivery>>('/deliveries?size=10&status=PICKUP_WAITING', { signal }),
  })
  const completed = useQuery({
    queryKey: ['deliveries', 'count', 'DELIVERED'],
    queryFn: ({ signal }) =>
      api<Page<Delivery>>('/deliveries?size=10&status=DELIVERED', { signal }),
  })
  const canceled = useQuery({
    queryKey: ['orders', 'count', 'CANCELED'],
    queryFn: ({ signal }) => api<Page<Order>>('/orders?size=10&status=CANCELED', { signal }),
  })
  const cards = [
    {
      label: '오늘 접수된 주문',
      data: todayOrders,
      icon: Package,
      note: `${today} · 한국 시간`,
      color: 'teal',
    },
    {
      label: '집하 대기 배송',
      data: waiting,
      icon: Truck,
      note: '출발 처리가 필요한 배송',
      color: 'amber',
    },
    {
      label: '배송 완료',
      data: completed,
      icon: CheckCheck,
      note: '전체 기간 누적 · 삭제 제외',
      color: 'blue',
    },
    {
      label: '취소된 주문',
      data: canceled,
      icon: ArrowDownLeft,
      note: '전체 기간 누적 · 삭제 제외',
      color: 'gray',
    },
  ]
  return (
    <>
      <PageTitle
        eyebrow="OPERATIONS OVERVIEW"
        title="물류 운영 현황"
        description={`${user?.name}님, 오늘의 주문과 배송 흐름을 한눈에 확인하세요.`}
        actions={
          <Button asChild>
            <Link to="/orders/new">
              <Plus size={17} />새 주문 등록
            </Link>
          </Button>
        }
      />
      <SetupGuide />
      <div className="overview-banner">
        <div>
          <span className="connection-dot" />
          <strong>주문에서 배송까지, 연결된 운영</strong>
          <span>실제 서비스 데이터로 현재 처리 현황을 확인합니다.</span>
        </div>
        <span>{today.replaceAll('-', '. ')}</span>
      </div>
      <div className="stat-grid">
        {cards.map(({ label, data, icon: Icon, note, color }) => (
          <article className="stat-card" key={label}>
            <div>
              <span>{label}</span>
              <span className={`stat-icon ${color}`}>
                <Icon size={20} />
              </span>
            </div>
            <strong>
              {data.isError ? '조회 오류' : (data.data?.totalElements.toLocaleString() ?? '—')}
              {data.data && <small>건</small>}
            </strong>
            <p>{note}</p>
            {data.isError && (
              <button className="text-button" onClick={() => void data.refetch()}>
                다시 조회
              </button>
            )}
          </article>
        ))}
      </div>
      <div className="dashboard-grid">
        <section className="panel">
          <header className="panel-heading">
            <div>
              <h2>최근 주문</h2>
              <p>가장 최근에 접수된 주문입니다.</p>
            </div>
            <Link className="text-link" to="/orders">
              전체 보기
              <ArrowRight size={15} />
            </Link>
          </header>
          <ErrorNotice error={orders.error} retry={() => void orders.refetch()} />
          {orders.isPending ? (
            <Loading />
          ) : orders.data?.content.length ? (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>주문 / 상품</th>
                    <th>수령업체</th>
                    <th>상태</th>
                    <th className="numeric">주문 금액</th>
                  </tr>
                </thead>
                <tbody>
                  {orders.data.content.slice(0, 6).map((o) => (
                    <tr key={o.orderId}>
                      <td>
                        <Link className="row-link" to={`/orders/${o.orderId}`}>
                          {o.productName}
                        </Link>
                        <small className="subline mono">ORD-{shortId(o.orderId)}</small>
                      </td>
                      <td>{directory.companyName(o.receiverCompanyId)}</td>
                      <td>
                        <Badge status={o.status} />
                      </td>
                      <td className="numeric">{money(o.totalPrice)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            !orders.isError && <Empty text="아직 접수된 주문이 없습니다." />
          )}
        </section>
        <section className="panel">
          <header className="panel-heading">
            <div>
              <h2>배송 흐름</h2>
              <p>최근 생성된 배송의 현재 상태입니다.</p>
            </div>
            <Link className="text-link" to="/deliveries">
              <ArrowRight size={18} aria-label="배송 전체 보기" />
            </Link>
          </header>
          <ErrorNotice error={deliveries.error} retry={() => void deliveries.refetch()} />
          {deliveries.isPending ? (
            <Loading />
          ) : deliveries.data?.content.length ? (
            <div className="delivery-feed">
              {deliveries.data.content.slice(0, 4).map((d) => (
                <Link key={d.deliveryId} to={`/deliveries/${d.deliveryId}`}>
                  <span className="feed-icon">
                    <Truck size={19} />
                  </span>
                  <div>
                    <strong>{directory.companyName(d.receiverCompanyId)}</strong>
                    <span>
                      {directory.hubName(d.destinationHubId)} · {dateTime(d.createdAt)}
                    </span>
                  </div>
                  <Badge status={d.status} />
                </Link>
              ))}
            </div>
          ) : (
            !deliveries.isError && <Empty text="아직 생성된 배송이 없습니다." />
          )}
        </section>
      </div>
      <section className="workflow-card">
        <div>
          <span className="eyebrow">YOUR DAILY WORKFLOW</span>
          <h2>오늘의 업무, 이 순서로 시작하세요.</h2>
          <p>주문을 등록하면 배송과 구간 담당자가 자동으로 연결됩니다.</p>
        </div>
        <div className="workflow-stages">
          {['주문 접수', '공급업체 집하', '수령업체 배송', '배송 완료'].map((step, index) => (
            <div key={step}>
              <span>{String(index + 1).padStart(2, '0')}</span>
              <strong>{step}</strong>
              {index < 3 && <ArrowRight size={15} />}
            </div>
          ))}
        </div>
      </section>
    </>
  )
}
