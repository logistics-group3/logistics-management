import { useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, Check, Package, ShieldCheck } from 'lucide-react'
import { useAuth } from '../app/auth'
import { api } from '../shared/api'
import { money } from '../shared/format'
import { allPages, useDirectory } from '../shared/queries'
import { clearPending, creationFailure, loadPending, savePending } from '../shared/order-request'
import type { PendingOrder } from '../shared/order-request'
import type { Order, OrderInput, Product } from '../shared/types'
import { ErrorNotice, Field } from '../shared/ui'
import { Button } from '../components/ui/button'
export function CreateOrder() {
  const { user, notify } = useAuth()
  const [saved] = useState(() => {
    try {
      return { pending: loadPending(user!.userId), error: undefined }
    } catch (error) {
      return { pending: null, error }
    }
  })
  const [pending, setPending] = useState<PendingOrder | null>(saved.pending)
  const [form, setForm] = useState<OrderInput>(
    saved.pending?.body ?? {
      supplierCompanyId: '',
      receiverCompanyId: '',
      productId: '',
      quantity: 1,
      requestNote: '',
    },
  )
  const [companySearch, setCompanySearch] = useState('')
  const [error, setError] = useState<unknown>(saved.error)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState('')
  const inFlight = useRef(false)
  const directory = useDirectory()
  const navigate = useNavigate()
  const cache = useQueryClient()
  const products = useQuery({
    queryKey: ['products', 'selection', form.supplierCompanyId],
    queryFn: ({ signal }) =>
      allPages<Product>(`/products?companyId=${form.supplierCompanyId}`, signal),
    enabled: Boolean(form.supplierCompanyId),
  })
  const product = products.data?.find((p) => p.productId === form.productId)
  const supplier = directory.companies.data?.find((c) => c.companyId === form.supplierCompanyId)
  const receiver = directory.companies.data?.find((c) => c.companyId === form.receiverCompanyId)
  const crossHub = supplier && receiver && supplier.hubId !== receiver.hubId
  const total =
    product && Number.isInteger(form.quantity) && form.quantity > 0
      ? BigInt(product.price) * BigInt(form.quantity)
      : 0n
  const overflow = total > 9223372036854775807n
  const locked = Boolean(pending) || busy || Boolean(saved.error)
  const companies = directory.companies.data?.filter(
    (c) =>
      c.name.includes(companySearch) ||
      c.companyId === form.supplierCompanyId ||
      c.companyId === form.receiverCompanyId,
  )
  async function submit() {
    if (inFlight.current || saved.error) return
    if (
      !pending &&
      (!product ||
        !supplier ||
        !receiver ||
        supplier.companyId === receiver.companyId ||
        overflow ||
        crossHub)
    )
      return
    inFlight.current = true
    setBusy(true)
    setError(undefined)
    try {
      const request = pending ?? {
        key: crypto.randomUUID(),
        body: { ...form },
        createdAt: new Date().toISOString(),
      }
      // Note: HTTP 호출 전에 원문과 키를 보관한다. 응답 유실·재로그인 후에도 동일 요청만 재전송한다.
      savePending(user!.userId, request)
      setPending(request)
      const result = await api<Order>('/orders', {
        method: 'POST',
        body: request.body,
        key: request.key,
      })
      if (!result?.orderId)
        throw new Error('주문 결과를 확인할 수 없습니다. 같은 요청으로 다시 확인해 주세요.')
      clearPending(user!.userId)
      setPending(null)
      await Promise.all([
        cache.invalidateQueries({ queryKey: ['orders'] }),
        cache.invalidateQueries({ queryKey: ['deliveries'] }),
      ])
      notify('주문이 접수되었습니다. 배송과 구간 담당자가 연결되었습니다.')
      navigate(`/orders/${result.orderId}`)
    } catch (value) {
      setError(value)
      const next = creationFailure(value)
      setFailure(next)
      if (next === 'edit') {
        clearPending(user!.userId)
        setPending(null)
      }
    } finally {
      inFlight.current = false
      setBusy(false)
    }
  }
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        void submit()
      }}
    >
      <div className="form-intro">
        <ShieldCheck size={19} />
        <span>한 번의 주문 접수로 배송까지 자동 생성됩니다.</span>
      </div>
      {pending && (
        <div className="notice warning">
          <strong>기존 요청의 결과를 확인해 주세요.</strong>
          <p>
            중복 주문을 방지하기 위해 처음 제출한 내용을 유지합니다. 다시 로그인한 뒤에도 이 탭에서
            이어갈 수 있습니다.
          </p>
          <details>
            <summary>요청 번호</summary>
            <code>{pending.key}</code>
          </details>
        </div>
      )}
      <ErrorNotice error={error} />
      <ErrorNotice
        error={directory.companies.error}
        retry={() => void directory.companies.refetch()}
      />
      <fieldset disabled={locked}>
        <section className="detail-section">
          <h3>
            <span className="step-number">01</span>업체 선택
          </h3>
          <Field label="업체 이름으로 찾기">
            <input
              type="search"
              value={companySearch}
              onChange={(e) => setCompanySearch(e.target.value)}
              placeholder="업체 이름 검색"
            />
          </Field>
          <div className="form-grid">
            <Field label="공급업체">
              <select
                required
                value={form.supplierCompanyId}
                onChange={(e) =>
                  setForm({ ...form, supplierCompanyId: e.target.value, productId: '' })
                }
              >
                <option value="">공급업체 선택</option>
                {companies?.map((c) => (
                  <option
                    key={c.companyId}
                    value={c.companyId}
                    disabled={c.companyId === form.receiverCompanyId}
                  >
                    {c.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="수령업체">
              <select
                required
                value={form.receiverCompanyId}
                onChange={(e) => setForm({ ...form, receiverCompanyId: e.target.value })}
              >
                <option value="">수령업체 선택</option>
                {companies
                  ?.filter((c) => user?.role === 'MASTER' || c.managerId === user?.userId)
                  .map((c) => (
                    <option
                      key={c.companyId}
                      value={c.companyId}
                      disabled={c.companyId === form.supplierCompanyId}
                    >
                      {c.name}
                    </option>
                  ))}
              </select>
            </Field>
          </div>
          {user?.role !== 'MASTER' &&
            directory.companies.isSuccess &&
            !directory.companies.data?.some((company) => company.managerId === user?.userId) && (
              <div className="notice">
                연결된 수령업체가 없습니다. 업체를 등록하면 본인이 담당자로 연결됩니다.
                <div className="actions">
                  <Button asChild>
                    <Link to="/companies?create=1">내 업체 등록하기</Link>
                  </Button>
                </div>
              </div>
            )}
          {receiver && (
            <div className="address-preview">
              <strong>배송지 · {directory.hubName(receiver.hubId)}</strong>
              <span>{receiver.address}</span>
            </div>
          )}
          {crossHub && (
            <div className="notice warning">
              다른 허브 간 주문은 허브 경로 API 연동 준비 중입니다. 현재 테스트는 같은 허브의 업체를
              선택해 주세요.
            </div>
          )}
        </section>
        <section className="detail-section">
          <h3>
            <span className="step-number">02</span>상품 및 수량
          </h3>
          <ErrorNotice error={products.error} retry={() => void products.refetch()} />
          <Field label="상품">
            <select
              value={form.productId}
              required
              disabled={!form.supplierCompanyId || locked}
              onChange={(e) => setForm({ ...form, productId: e.target.value })}
            >
              <option value="">
                {form.supplierCompanyId ? '상품 선택' : '공급업체를 먼저 선택하세요'}
              </option>
              {products.data?.map((p) => (
                <option key={p.productId} value={p.productId}>
                  {p.name} · {money(p.price)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="주문 수량">
            <input
              type="number"
              min={1}
              max={2147483647}
              step={1}
              required
              value={form.quantity || ''}
              onChange={(e) => setForm({ ...form, quantity: Number(e.target.value) })}
            />
          </Field>
          {product && (
            <div className="price-preview">
              <span>
                <Package size={16} />
                {product.description || product.name}
              </span>
              <div>
                <span>
                  {money(product.price)} × {form.quantity || 0}개
                </span>
                <strong>{money(total)}</strong>
              </div>
            </div>
          )}
          {overflow && (
            <div className="notice error">
              총 금액이 서버의 허용 범위를 초과합니다. 수량을 줄여 주세요.
            </div>
          )}
        </section>
        <section className="detail-section">
          <h3>
            <span className="step-number">03</span>요청사항
          </h3>
          <Field label="배송 요청사항 (선택)" hint={`${form.requestNote.length} / 500자`}>
            <textarea
              rows={3}
              maxLength={500}
              placeholder="예: 입고장 담당자에게 도착 전 연락 바랍니다."
              value={form.requestNote}
              onChange={(e) => setForm({ ...form, requestNote: e.target.value })}
            />
          </Field>
        </section>
      </fieldset>
      <div className="order-form-footer">
        <span>
          <Check size={15} />
          최종 금액은 주문 접수 시 확정됩니다.
        </span>
        <Button
          type="submit"
          disabled={
            busy ||
            Boolean(saved.error) ||
            failure === 'blocked' ||
            failure === 'aborted' ||
            (!pending && (!product || !receiver || !supplier || overflow || Boolean(crossHub)))
          }
        >
          {busy ? '주문 결과 확인 중…' : pending ? '같은 요청 결과 확인' : '주문 등록'}
          <ArrowRight size={16} />
        </Button>
      </div>
      {failure === 'aborted' && (
        <Button
          type="button"
          variant="secondary"
          onClick={() => {
            clearPending(user!.userId)
            setPending(null)
            setFailure('')
            setError(undefined)
          }}
        >
          종료된 요청을 정리하고 다시 작성
        </Button>
      )}
    </form>
  )
}
