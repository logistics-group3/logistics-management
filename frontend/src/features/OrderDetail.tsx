import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, Package } from 'lucide-react'
import { api } from '../shared/api'
import type { Delivery, Order } from '../shared/types'
import { useDirectory } from '../shared/queries'
import { dateTime, money } from '../shared/format'
import { Badge, DetailField, ErrorNotice, Field, Loading } from '../shared/ui'
import { Button } from '../components/ui/button'
import { Dialog } from '../components/ui/dialog'
import { useAuth } from '../app/auth'
export function OrderDetail({ id }: { id: string }) {
  const { user, notify } = useAuth()
  const navigate = useNavigate()
  const cache = useQueryClient()
  const directory = useDirectory()
  const order = useQuery({
    queryKey: ['orders', id],
    queryFn: ({ signal }) => api<Order>(`/orders/${id}`, { signal }),
  })
  const delivery = useQuery({
    queryKey: ['deliveries', order.data?.deliveryId],
    queryFn: ({ signal }) => api<Delivery>(`/deliveries/${order.data!.deliveryId}`, { signal }),
    enabled: Boolean(order.data?.deliveryId),
  })
  const [action, setAction] = useState<'cancel' | 'delete' | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  async function mutate(method: string, suffix = '', body?: unknown) {
    if (busy) return
    setBusy(true)
    setError(undefined)
    try {
      await api(`/orders/${id}${suffix}`, { method, body })
      notify(
        method === 'DELETE'
          ? '주문이 삭제되었습니다.'
          : suffix
            ? '주문이 취소되었습니다.'
            : '요청사항이 수정되었습니다.',
      )
      setAction(null)
      if (method === 'DELETE') navigate('/orders')
    } catch (value) {
      setError(value)
      setAction(null)
    } finally {
      setBusy(false)
      void cache.invalidateQueries({ queryKey: ['orders'] })
      void cache.invalidateQueries({ queryKey: ['deliveries'] })
    }
  }
  if (order.isPending) return <Loading />
  if (order.isError) return <ErrorNotice error={order.error} retry={() => void order.refetch()} />
  const o = order.data
  return (
    <>
      <div className="detail-hero">
        <span className="detail-icon">
          <Package size={26} />
        </span>
        <div>
          <h3>{o.productName}</h3>
          <span>{dateTime(o.createdAt)} 접수</span>
        </div>
        <Badge status={o.status} />
      </div>
      <ErrorNotice error={error} />
      <section className="detail-section">
        <h3>주문 정보</h3>
        <dl className="detail-grid">
          <DetailField label="공급업체">{directory.companyName(o.supplierCompanyId)}</DetailField>
          <DetailField label="수령업체">{directory.companyName(o.receiverCompanyId)}</DetailField>
          <DetailField label="단가">{money(o.unitPrice)}</DetailField>
          <DetailField label="수량">{o.quantity.toLocaleString()}개</DetailField>
        </dl>
        <div className="price-total">
          <span>총 주문 금액</span>
          <strong>{money(o.totalPrice)}</strong>
        </div>
      </section>
      <section className="detail-section">
        <div className="section-heading">
          <h3>배송 현황</h3>
          <Link className="text-link" to={`/deliveries/${o.deliveryId}`}>
            배송 상세
            <ArrowRight size={14} />
          </Link>
        </div>
        {delivery.isPending ? (
          <Loading />
        ) : delivery.isError ? (
          <ErrorNotice error={delivery.error} retry={() => void delivery.refetch()} />
        ) : (
          <div className="delivery-summary">
            <Badge status={delivery.data.status} />
            <span>{delivery.data.deliveryAddress}</span>
          </div>
        )}
      </section>
      <section className="detail-section">
        <h3>요청사항</h3>
        <form
          key={o.updatedAt}
          onSubmit={(event) => {
            event.preventDefault()
            const note = String(new FormData(event.currentTarget).get('requestNote') ?? '')
            if (!note.trim()) {
              setError(new Error('요청사항을 공백 없이 입력해 주세요.'))
              return
            }
            void mutate('PATCH', '', { requestNote: note })
          }}
        >
          <Field label="배송 요청사항" hint="최대 500자 · 취소된 주문은 수정할 수 없습니다.">
            <textarea
              name="requestNote"
              aria-label="배송 요청사항"
              rows={4}
              maxLength={500}
              required
              defaultValue={o.requestNote ?? ''}
              disabled={busy || o.status === 'CANCELED'}
              placeholder="담당자에게 전달할 내용을 입력하세요."
            />
          </Field>
          {o.status !== 'CANCELED' && (
            <div className="actions align-end">
              <Button type="submit" variant="secondary" disabled={busy}>
                요청사항 저장
              </Button>
            </div>
          )}
        </form>
      </section>
      <details className="technical-details">
        <summary>주문 식별 정보</summary>
        <dl>
          <DetailField label="주문 ID">
            <code>{o.orderId}</code>
          </DetailField>
          <DetailField label="배송 ID">
            <code>{o.deliveryId}</code>
          </DetailField>
          <DetailField label="주문자 ID">
            <code>{o.ordererId}</code>
          </DetailField>
        </dl>
      </details>
      <div className="detail-actions">
        {o.status !== 'CANCELED' && (
          <Button variant="secondary" disabled={busy} onClick={() => setAction('cancel')}>
            주문 취소
          </Button>
        )}
        {user?.role === 'MASTER' && (
          <Button variant="danger" disabled={busy} onClick={() => setAction('delete')}>
            주문 삭제
          </Button>
        )}
      </div>
      <p className="muted small">
        취소는 집하 시작 전에만, 삭제는 취소 또는 배송 완료 후에 가능합니다.
      </p>
      <Dialog
        open={Boolean(action)}
        onClose={() => {
          if (!busy) setAction(null)
        }}
        title={action === 'delete' ? '주문 삭제 확인' : '주문 취소 확인'}
        description={`${o.productName} · ${o.quantity}개 · ${money(o.totalPrice)}`}
      >
        <p>
          {action === 'delete'
            ? '주문과 연결된 배송이 조회 목록에서 제외됩니다. 취소되었거나 배송이 완료된 주문만 삭제할 수 있습니다.'
            : '집하가 시작되지 않은 배송과 주문이 함께 취소됩니다. 취소 후에는 요청사항을 변경할 수 없습니다.'}
        </p>
        <div className="actions align-end">
          <Button variant="secondary" disabled={busy} onClick={() => setAction(null)}>
            돌아가기
          </Button>
          <Button
            variant="danger"
            disabled={busy}
            onClick={() =>
              void mutate(
                action === 'delete' ? 'DELETE' : 'POST',
                action === 'cancel' ? '/cancel' : '',
              )
            }
          >
            {busy ? '처리 중…' : action === 'delete' ? '삭제 확인' : '취소 확인'}
          </Button>
        </div>
      </Dialog>
    </>
  )
}
