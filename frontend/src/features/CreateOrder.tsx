import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, Check, Package, ShieldCheck } from 'lucide-react'
import { useAuth } from '../app/auth'
import { ApiError, api } from '../shared/api'
import { money } from '../shared/format'
import { allPages, useDirectory } from '../shared/queries'
import {
  CREATION_POLL_INTERVAL_MS,
  CREATION_POLL_LIMIT_MS,
  classifyCreation,
  clearPending,
  creationFailure,
  creationStep,
  loadPending,
  savePending,
} from '../shared/order-request'
import type { PendingOrder } from '../shared/order-request'
import type { Order, OrderAccepted, OrderCreation, OrderInput, Product } from '../shared/types'
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
  const [phase, setPhase] = useState<'' | 'polling' | 'timeout' | 'recovery'>('')
  const [help, setHelp] = useState<string>()
  const inFlight = useRef(false)
  const abort = useRef(new AbortController())
  // 언마운트·닫기 시 폴링을 멈춘다. StrictMode 재마운트에 대비해 새 컨트롤러를 만든다.
  useEffect(() => {
    abort.current = new AbortController()
    return () => abort.current.abort()
  }, [])
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
  async function finish(orderId: string) {
    clearPending(user!.userId)
    setPending(null)
    setPhase('')
    await Promise.all([
      cache.invalidateQueries({ queryKey: ['orders'] }),
      cache.invalidateQueries({ queryKey: ['deliveries'] }),
    ])
    notify('주문이 접수되었습니다. 배송과 구간 담당자가 연결되었습니다.')
    navigate(`/orders/${orderId}`)
  }
  // 1초 간격으로 진행을 조회한다. 일시적 네트워크·서버 오류는 한도 안에서 계속 조회한다.
  async function watch(orderId: string) {
    const signal = abort.current.signal
    const started = Date.now()
    for (;;) {
      let progress: OrderCreation | undefined
      try {
        progress = await api<OrderCreation>(`/orders/creations/${orderId}`, { signal })
      } catch (value) {
        if (signal.aborted) throw value
        if (!(value instanceof ApiError) || value.status === 0 || value.status >= 500) {
          if (Date.now() - started >= CREATION_POLL_LIMIT_MS) return { kind: 'timeout' as const }
        } else throw value
      }
      if (progress) {
        const step = creationStep(progress, Date.now() - started)
        if (step !== 'continue') return { kind: step, progress }
      }
      await new Promise((resolve) => setTimeout(resolve, CREATION_POLL_INTERVAL_MS))
      if (signal.aborted) throw new DOMException('aborted', 'AbortError')
    }
  }
  async function submit() {
    if (inFlight.current || saved.error) return
    if (
      !pending &&
      (!product || !supplier || !receiver || supplier.companyId === receiver.companyId || overflow)
    )
      return
    inFlight.current = true
    let polling = false
    setBusy(true)
    setError(undefined)
    setHelp(undefined)
    setFailure('')
    try {
      const request = pending ?? {
        key: crypto.randomUUID(),
        body: { ...form },
        createdAt: new Date().toISOString(),
      }
      // Note: HTTP 호출 전에 원문과 키를 보관한다. 응답 유실·재로그인 후에도 동일 요청만 재전송한다.
      savePending(user!.userId, request)
      setPending(request)
      let orderId = request.orderId
      if (!orderId) {
        const result = await api<Order | OrderAccepted>('/orders', {
          method: 'POST',
          body: request.body,
          key: request.key,
        })
        const accepted = classifyCreation(result)
        if (accepted.kind === 'unknown')
          throw new Error('주문 결과를 확인할 수 없습니다. 같은 요청으로 다시 확인해 주세요.')
        if (accepted.kind === 'created') return await finish(accepted.order.orderId)
        orderId = accepted.orderId
        // Note: 접수된 주문 번호를 저장해 새로고침 뒤에는 POST 없이 같은 건의 진행만 조회한다.
        savePending(user!.userId, { ...request, orderId })
        setPending({ ...request, orderId })
      }
      setPhase('polling')
      polling = true
      const step = await watch(orderId)
      polling = false
      if (step.kind === 'completed') return await finish(orderId)
      if (step.kind === 'aborted') {
        clearPending(user!.userId)
        setPending(null)
        setPhase('')
        setFailure('')
        setError(new Error(step.progress.failureMessage ?? '주문 생성이 종료되었습니다.'))
        setHelp(
          '주문은 만들어지지 않았습니다. 원인을 해결한 뒤 다시 주문하면 새 요청으로 처리됩니다.',
        )
      } else if (step.kind === 'recovery') {
        setPhase('recovery')
        setFailure('blocked')
      } else if (step.kind === 'timeout') setPhase('timeout')
    } catch (value) {
      if (abort.current.signal.aborted) return
      setPhase('')
      setHelp(undefined)
      setError(value)
      // 진행 조회 자체가 거절되면 결과를 알 수 없으므로 같은 키를 유지한 채 막는다.
      const next = polling && value instanceof ApiError ? 'blocked' : creationFailure(value)
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
      {phase === 'polling' && (
        <div className="notice" role="status">
          주문을 처리하고 있습니다. 배송과 구간 담당자를 연결하는 중이며 이 화면을 닫지 마세요.
        </div>
      )}
      {phase === 'timeout' && (
        <div className="notice warning" role="status">
          <strong>처리가 길어지고 있습니다.</strong>
          <p>
            새 주문을 만들지 말고 아래 버튼으로 같은 요청의 결과를 다시 확인해 주세요. 요청 번호가
            같아 중복 주문은 생기지 않습니다.
          </p>
        </div>
      )}
      {phase === 'recovery' && (
        <div className="notice warning" role="status">
          <strong>결과 확인이 필요합니다.</strong>
          <p>
            같은 요청을 다시 보내지 말고 주문 목록에서 확인하세요. 요청 번호를 관리자에게 전달할 수
            있습니다.
          </p>
        </div>
      )}
      <ErrorNotice error={error} help={help} />
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
            <div className="notice" role="status">
              다른 허브 간 주문입니다. 배송은 공급업체 집하 → 허브 간 이동(
              {directory.hubName(supplier.hubId)} → {directory.hubName(receiver.hubId)}) → 수령업체
              배송 3구간으로 만들어집니다. 각 구간의 출발 허브에 배송담당자가 있어야 합니다.
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
            (!pending && (!product || !receiver || !supplier || overflow))
          }
        >
          {busy ? '주문을 처리하고 있습니다…' : pending ? '같은 요청 결과 확인' : '주문 등록'}
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
