import { useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  CheckCheck,
  CircleDot,
  ClipboardList,
  Clock3,
  Package,
  Plus,
  RefreshCw,
  Truck,
} from 'lucide-react'
import { api } from '../shared/api'
import { dateTime, labels, localDate, money, shortId } from '../shared/format'
import { useDirectory } from '../shared/queries'
import { Badge, Empty, ErrorNotice, Loading, PageTitle } from '../shared/ui'
import { Button } from '../components/ui/button'
import type { Delivery, DeliveryStatus, Order, Page } from '../shared/types'
import { useAuth } from '../app/auth'
import { SetupGuide } from './SetupGuide'

const stages: DeliveryStatus[] = [
  'PICKUP_WAITING',
  'PICKING_UP',
  'HUB_WAITING',
  'HUB_MOVING',
  'DEST_HUB_ARRIVED',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'CANCELED',
]

export function Dashboard() {
  const { user } = useAuth()
  const directory = useDirectory()
  const cache = useQueryClient()
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
  const counts = useQueries({
    queries: stages.map((status) => ({
      queryKey: ['deliveries', 'count', status],
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        api<Page<Delivery>>(`/deliveries?size=10&status=${status}`, { signal }),
    })),
  })
  const countsReady = counts.every((query) => query.isSuccess)
  const total = countsReady ? counts.reduce((sum, query) => sum + query.data!.totalElements, 0) : 0
  const inProgress = counts.slice(1, 6)
  const progressing = inProgress.every((query) => query.isSuccess)
    ? inProgress.reduce((sum, query) => sum + query.data!.totalElements, 0)
    : undefined
  const busy =
    orders.isFetching || deliveries.isFetching || counts.some((query) => query.isFetching)
  const updatedAt = orders.dataUpdatedAt
  const metrics = [
    {
      label: '오늘 접수 주문',
      value: todayOrders.data?.totalElements,
      error: todayOrders.isError,
      icon: ClipboardList,
      tone: 'blue',
      note: '오늘 00:00 이후 접수 · 한국 시간',
      path: '/orders',
    },
    {
      label: '집하 대기 배송',
      value: counts[0].data?.totalElements,
      error: counts[0].isError,
      icon: Clock3,
      tone: 'amber',
      note: '집하 출발 처리가 필요한 배송',
      path: '/deliveries?status=PICKUP_WAITING',
    },
    {
      label: '진행 중 배송',
      value: progressing,
      error: inProgress.some((query) => query.isError),
      icon: Truck,
      tone: 'blue',
      note: '집하 중부터 최종 배송 중까지',
      path: '/deliveries',
    },
    {
      label: '배송 완료',
      value: counts[6].data?.totalElements,
      error: counts[6].isError,
      icon: CheckCheck,
      tone: 'green',
      note: '전체 기간 누적 · 삭제 제외',
      path: '/deliveries?status=DELIVERED',
    },
  ]
  return (
    <>
      <PageTitle
        title="물류 운영 현황"
        actions={
          <>
            <Button
              variant="secondary"
              disabled={busy}
              aria-label="운영 현황 새로고침"
              onClick={() => {
                void cache.invalidateQueries({ queryKey: ['orders'] })
                void cache.invalidateQueries({ queryKey: ['deliveries'] })
              }}
            >
              <RefreshCw size={15} className={busy ? 'spin' : ''} />
              새로고침
            </Button>
            <Button asChild>
              <Link to="/orders/new">
                <Plus size={16} />새 주문 등록
              </Link>
            </Button>
          </>
        }
      />
      <div className="workspace-context">
        <span>
          <CircleDot size={14} />
          {user?.isDeliveryManager && user.role !== 'MASTER'
            ? '배정된 배송 기준'
            : '현재 계정의 조회 권한 기준'}
        </span>
        <span>
          {today.replaceAll('-', '.')}
          <i />
          {updatedAt
            ? `마지막 조회 ${new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'Asia/Seoul' }).format(updatedAt)}`
            : '현황 조회 중'}
        </span>
      </div>
      <SetupGuide />
      <section className="stat-grid" aria-label="핵심 운영 지표">
        {metrics.map(({ label, value, error, icon: Icon, tone, note, path }) => (
          <Link className={`stat-card stat-${tone}`} key={label} to={path}>
            <div>
              <span>{label}</span>
              <Icon size={18} strokeWidth={1.7} />
            </div>
            <strong>
              {error ? '조회 오류' : (value?.toLocaleString() ?? '—')}
              {value !== undefined && <small>건</small>}
            </strong>
            <p>
              {note}
              <ArrowUpRight size={14} />
            </p>
          </Link>
        ))}
      </section>
      <section className="panel delivery-pipeline" aria-label="배송 단계별 현황">
        <header className="panel-heading">
          <div>
            <h2>
              <span className="section-marker" />
              배송 단계별 현황
            </h2>
            <p>전체 기간 · 삭제 제외</p>
          </div>
          <span className="panel-total">
            전체 <strong>{countsReady ? total.toLocaleString() : '—'}</strong>건
          </span>
        </header>
        <div className="pipeline-stages">
          {stages.map((status, index) => (
            <Link
              to={`/deliveries?status=${status}`}
              key={status}
              className={`pipeline-stage stage-${status.toLowerCase()}`}
            >
              <span className="pipeline-label">
                <i />
                {labels[status]}
              </span>
              <strong>
                {counts[index].isError
                  ? '오류'
                  : (counts[index].data?.totalElements.toLocaleString() ?? '—')}
                <small>건</small>
              </strong>
              <span className="pipeline-track" aria-hidden="true">
                <span
                  style={{
                    width: `${countsReady && total ? (counts[index].data!.totalElements / total) * 100 : 0}%`,
                  }}
                />
              </span>
            </Link>
          ))}
        </div>
        {counts.some((query) => query.isError) && (
          <ErrorNotice
            error={counts.find((query) => query.isError)?.error}
            retry={() => {
              void cache.invalidateQueries({ queryKey: ['deliveries', 'count'] })
            }}
          />
        )}
      </section>
      <div className="operations-grid">
        <section className="panel recent-orders">
          <header className="panel-heading">
            <div>
              <h2>
                <span className="section-marker" />
                최근 주문
              </h2>
            </div>
            <Link className="text-link" to="/orders">
              전체 보기
              <ArrowUpRight size={14} />
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
                    <th>주문 번호 / 상품</th>
                    <th>수령업체</th>
                    <th>주문 금액</th>
                    <th>상태</th>
                  </tr>
                </thead>
                <tbody>
                  {orders.data.content.slice(0, 8).map((order) => (
                    <tr key={order.orderId}>
                      <td>
                        <Link className="row-link" to={`/orders/${order.orderId}`}>
                          {order.productName}
                        </Link>
                        <span className="subline mono">ORD-{shortId(order.orderId)}</span>
                      </td>
                      <td>{directory.companyName(order.receiverCompanyId)}</td>
                      <td className="numeric strong">{money(order.totalPrice)}</td>
                      <td>
                        <Badge status={order.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            !orders.isError && <Empty text="아직 접수된 주문이 없습니다." />
          )}
          <div className="panel-caption">
            <Package size={13} />
            주문 상태와 배송 진행 상태는 별도로 관리됩니다.
          </div>
        </section>
        <aside className="operations-aside">
          <section className="dispatch-card">
            <div className="dispatch-card-heading">
              <span className="dispatch-symbol">
                <Truck size={23} />
              </span>
            </div>
            <h2>집하 대기 배송</h2>
            <Link to="/deliveries?status=PICKUP_WAITING">
              집하 대기 확인하기
              <ArrowRight size={16} />
            </Link>
            <div className="dispatch-grid" aria-hidden="true">
              <span />
              <span />
              <span />
              <span />
            </div>
          </section>
          <section className="panel quick-actions">
            <header className="panel-heading">
              <h2>업무 바로가기</h2>
            </header>
            <Link to="/orders/new">
              <span className="quick-icon">
                <Plus size={16} />
              </span>
              <div>
                <strong>주문 등록</strong>
              </div>
              <ArrowUpRight size={15} />
            </Link>
            <Link to="/companies">
              <span className="quick-icon">
                <Package size={16} />
              </span>
              <div>
                <strong>업체 관리</strong>
              </div>
              <ArrowUpRight size={15} />
            </Link>
            <Link to="/guide">
              <span className="quick-icon">
                <ClipboardList size={16} />
              </span>
              <div>
                <strong>운영 가이드</strong>
              </div>
              <ArrowUpRight size={15} />
            </Link>
          </section>
        </aside>
      </div>
      <section className="panel latest-deliveries">
        <header className="panel-heading">
          <div>
            <h2>
              <span className="section-marker" />
              최근 배송
            </h2>
          </div>
          <Link className="text-link" to="/deliveries">
            배송 전체 보기
            <ArrowUpRight size={14} />
          </Link>
        </header>
        <ErrorNotice error={deliveries.error} retry={() => void deliveries.refetch()} />
        {deliveries.isPending ? (
          <Loading />
        ) : deliveries.data?.content.length ? (
          <div className="delivery-board">
            {deliveries.data.content.slice(0, 4).map((delivery) => (
              <Link
                className="delivery-board-item"
                key={delivery.deliveryId}
                to={`/deliveries/${delivery.deliveryId}`}
              >
                <div>
                  <span className="mono">DLV-{shortId(delivery.deliveryId)}</span>
                  <Badge status={delivery.status} />
                </div>
                <strong>{directory.companyName(delivery.receiverCompanyId)}</strong>
                <div className="hub-connection">
                  <span>{directory.hubName(delivery.departureHubId)}</span>
                  <ArrowRight size={13} />
                  <span>{directory.hubName(delivery.destinationHubId)}</span>
                </div>
                <p>
                  {dateTime(delivery.createdAt)}
                  <ArrowUpRight size={14} />
                </p>
              </Link>
            ))}
          </div>
        ) : (
          !deliveries.isError && <Empty text="아직 생성된 배송이 없습니다." />
        )}
      </section>
      <div className="workspace-note">
        <ArrowDownLeft size={13} />
        조회 권한 내 데이터 · 자동 갱신 없음
      </div>
    </>
  )
}
