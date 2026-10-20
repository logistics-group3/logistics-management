import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ArrowRight, Check, MapPin, RefreshCw, Truck } from 'lucide-react'
import { api } from '../shared/api'
import { labels, dateTime, shortId } from '../shared/format'
import { useDirectory } from '../shared/queries'
import { Badge, DetailField, ErrorNotice, Field, Loading } from '../shared/ui'
import { nextRouteAction } from '../shared/delivery-rules'
import type { DeliveryDetail, DeliveryRoute, Page, User } from '../shared/types'
import { Button } from '../components/ui/button'
import { Dialog } from '../components/ui/dialog'
import { useAuth } from '../app/auth'
export function DeliverySheet({ id }: { id: string }) {
  const { user, notify } = useAuth()
  const cache = useQueryClient()
  const directory = useDirectory()
  const delivery = useQuery({
    queryKey: ['deliveries', id],
    queryFn: ({ signal }) => api<DeliveryDetail>(`/deliveries/${id}`, { signal }),
  })
  const users = useQuery({
    queryKey: ['users', 'route-names'],
    queryFn: ({ signal }) => api<Page<User>>('/users?size=50', { signal }),
    enabled: user?.role === 'MASTER',
  })
  const [target, setTarget] = useState<{
    route: DeliveryRoute
    status: 'MOVING' | 'ARRIVED'
  } | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  if (delivery.isPending) return <Loading />
  if (delivery.isError)
    return <ErrorNotice error={delivery.error} retry={() => void delivery.refetch()} />
  const d = delivery.data
  const routes = [...d.routes].sort((a, b) => a.sequence - b.sequence)
  const needActual = target?.route.routeType === 'HUB_TRANSFER' && target.status === 'ARRIVED'
  return (
    <>
      <div className="detail-hero">
        <span className="detail-icon">
          <Truck size={27} />
        </span>
        <div>
          <h3>{directory.companyName(d.receiverCompanyId)}</h3>
          <span>{dateTime(d.createdAt)} 생성</span>
        </div>
        <Badge status={d.status} />
      </div>
      <ErrorNotice error={error} />
      <section className="detail-section">
        <div className="section-heading">
          <h3>배송 정보</h3>
          <Button
            variant="ghost"
            onClick={() => void delivery.refetch()}
            aria-label="배송 상태 새로고침"
          >
            <RefreshCw size={16} />
          </Button>
        </div>
        <div className="address-route">
          <div>
            <MapPin size={18} />
            <span>
              <small>집하 주소 · {directory.companyName(d.supplierCompanyId)}</small>
              <strong>{d.pickupAddress}</strong>
            </span>
          </div>
          <div>
            <MapPin size={18} />
            <span>
              <small>배송 주소 · {directory.companyName(d.receiverCompanyId)}</small>
              <strong>{d.deliveryAddress}</strong>
            </span>
          </div>
        </div>
        <dl className="detail-grid">
          <DetailField label="수령인">{d.recipientName}</DetailField>
          <DetailField label="연결된 주문">
            {user?.role === 'MASTER' || !user?.isDeliveryManager ? (
              <Link className="text-link mono" to={`/orders/${d.orderId}`}>
                ORD-{shortId(d.orderId)}
                <ArrowRight size={13} />
              </Link>
            ) : (
              `ORD-${shortId(d.orderId)}`
            )}
          </DetailField>
        </dl>
      </section>
      <section className="detail-section">
        <div className="section-heading">
          <h3>배송 구간</h3>
          <span className="small muted">
            {routes.filter((r) => r.status === 'ARRIVED').length} / {routes.length} 구간 완료
          </span>
        </div>
        <div className="route-timeline">
          {routes.map((route) => {
            const next = nextRouteAction(d, route, user!)
            const manager =
              route.managerId === user?.userId
                ? `${user.name} (나)`
                : (users.data?.content.find((u) => u.userId === route.managerId)?.name ??
                  shortId(route.managerId))
            return (
              <article
                className={`route-step route-${route.status.toLowerCase()}`}
                key={route.deliveryRouteId}
              >
                <span className="route-node">
                  {route.status === 'ARRIVED' ? <Check size={16} /> : route.sequence}
                </span>
                <div className="route-card">
                  <div className="section-heading">
                    <h4>{labels[route.routeType]}</h4>
                    <Badge status={route.status} />
                  </div>
                  <p>
                    {route.departureHubId
                      ? directory.hubName(route.departureHubId)
                      : directory.companyName(d.supplierCompanyId)}
                    <ArrowRight size={13} />
                    {route.arrivalHubId
                      ? directory.hubName(route.arrivalHubId)
                      : directory.companyName(d.receiverCompanyId)}
                  </p>
                  <span className="small muted">담당자 · {manager}</span>
                  {route.expectedDistanceKm != null && (
                    <span className="subline">
                      예상 {route.expectedDistanceKm}km · {route.expectedDurationMinutes}분
                    </span>
                  )}
                  {route.actualDistanceKm != null && (
                    <span className="subline">
                      실제 {route.actualDistanceKm}km · {route.actualDurationMinutes}분
                    </span>
                  )}
                  {next && (
                    <div className="route-action">
                      <Button
                        variant={next === 'MOVING' ? 'secondary' : 'primary'}
                        disabled={busy}
                        onClick={() => {
                          setError(undefined)
                          setTarget({ route, status: next })
                        }}
                      >
                        {next === 'MOVING' ? '출발 처리' : '도착 처리'}
                        <ArrowRight size={14} />
                      </Button>
                    </div>
                  )}
                </div>
              </article>
            )
          })}
        </div>
        {d.departureHubId === d.destinationHubId && (
          <p className="muted small">
            같은 허브에 속한 업체 간 배송으로 허브 간 이동 구간이 생략됩니다.
          </p>
        )}
      </section>
      <details className="technical-details">
        <summary>배송 식별 정보</summary>
        <code>{d.deliveryId}</code>
      </details>
      <Dialog
        open={Boolean(target)}
        onClose={() => {
          if (!busy) setTarget(null)
        }}
        title={`${target ? labels[target.route.routeType] : ''} ${target?.status === 'MOVING' ? '출발' : '도착'} 처리`}
        description="선택한 구간의 실제 작업 완료 여부를 확인하세요."
      >
        <form
          onSubmit={async (event) => {
            event.preventDefault()
            if (busy || !target) return
            const form = new FormData(event.currentTarget)
            setBusy(true)
            setError(undefined)
            try {
              await api(`/deliveries/${id}/routes/${target.route.deliveryRouteId}/status`, {
                method: 'PATCH',
                body: {
                  status: target.status,
                  ...(needActual
                    ? {
                        actualDistanceKm: Number(form.get('distance')),
                        actualDurationMinutes: Number(form.get('duration')),
                      }
                    : {}),
                },
              })
              notify('배송 구간 상태가 변경되었습니다.')
            } catch (value) {
              setError(value)
            } finally {
              setBusy(false)
              setTarget(null)
              void cache.invalidateQueries({ queryKey: ['deliveries'] })
            }
          }}
        >
          {needActual && (
            <div className="form-grid">
              <Field label="실제 거리 (km)">
                <input name="distance" type="number" min={0} max={999999.99} step={0.01} required />
              </Field>
              <Field label="실제 소요 시간 (분)">
                <input name="duration" type="number" min={0} max={2147483647} step={1} required />
              </Field>
            </div>
          )}
          <p>변경 후 이전 상태로 되돌릴 수 없습니다.</p>
          <div className="actions align-end">
            <Button
              type="button"
              variant="secondary"
              disabled={busy}
              onClick={() => setTarget(null)}
            >
              돌아가기
            </Button>
            <Button disabled={busy}>{busy ? '처리 중…' : '상태 변경 확인'}</Button>
          </div>
        </form>
      </Dialog>
    </>
  )
}
