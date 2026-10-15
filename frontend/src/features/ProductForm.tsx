import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Package, Plus } from 'lucide-react'
import { useAuth } from '../app/auth'
import { api, ApiError } from '../shared/api'
import { canManageCompany, parseWonPrice } from '../shared/catalog-rules'
import { useDirectory } from '../shared/queries'
import { money } from '../shared/format'
import type { Product } from '../shared/types'
import { ErrorNotice, Field } from '../shared/ui'
import { Button } from '../components/ui/button'
export function ProductForm({
  companyId = '',
  onSaved,
  onInspect,
}: {
  companyId?: string
  onSaved: (product: Product) => void
  onInspect: (name: string) => void
}) {
  const { user, notify } = useAuth()
  const directory = useDirectory()
  const owned = directory.companies.data?.filter((c) => canManageCompany(user!, c))
  const [values, setValues] = useState({ companyId, name: '', description: '', price: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [uncertain, setUncertain] = useState(false)
  const inFlight = useRef(false)
  const cache = useQueryClient()
  let preview: string | undefined
  try {
    preview = money(parseWonPrice(values.price))
  } catch {
    /* 입력 중인 값은 미리 표시하지 않는다. */
  }
  if (directory.companies.isSuccess && !owned?.length)
    return (
      <div className="notice">
        먼저 담당 업체를 등록해야 상품을 추가할 수 있습니다.
        <div className="actions">
          <Button asChild>
            <Link to="/companies?create=1">내 업체 등록하기</Link>
          </Button>
        </div>
      </div>
    )
  return (
    <form
      onSubmit={async (event) => {
        event.preventDefault()
        if (inFlight.current || uncertain) return
        setError(undefined)
        let price: bigint
        try {
          price = parseWonPrice(values.price)
          if (!values.name.trim()) throw new Error('상품명을 입력해 주세요.')
          if (!owned?.some((c) => c.companyId === values.companyId))
            throw new Error('상품을 등록할 담당 업체를 선택해 주세요.')
        } catch (value) {
          setError(value)
          return
        }
        inFlight.current = true
        setBusy(true)
        try {
          const result = await api<Product>(`/companies/${values.companyId}/products`, {
            method: 'POST',
            body: { name: values.name, description: values.description || null, price },
          })
          if (!result?.productId)
            throw new ApiError(0, 'INVALID_RESPONSE', '상품 저장 결과를 확인할 수 없습니다.')
          await cache.invalidateQueries({ queryKey: ['products'] })
          notify('상품이 등록되었습니다. 주문에서 선택할 수 있습니다.')
          onSaved(result)
        } catch (value) {
          setError(value)
          if (value instanceof ApiError && (value.status === 0 || value.status >= 500))
            setUncertain(true)
        } finally {
          inFlight.current = false
          setBusy(false)
        }
      }}
    >
      <div className="form-intro">
        <Package size={19} />
        <span>담당 업체가 공급하는 상품과 원화 단가를 등록합니다.</span>
      </div>
      <ErrorNotice
        error={error}
        help={
          uncertain
            ? '등록이 완료되었을 수도 있습니다. 다시 등록하기 전에 상품 목록을 확인해 주세요.'
            : undefined
        }
      />
      <ErrorNotice
        error={directory.companies.error}
        retry={() => void directory.companies.refetch()}
      />
      <fieldset disabled={busy || uncertain}>
        <Field label="공급업체">
          <select
            required
            value={values.companyId}
            onChange={(e) => setValues({ ...values, companyId: e.target.value })}
          >
            <option value="">담당 업체 선택</option>
            {owned?.map((c) => (
              <option value={c.companyId} key={c.companyId}>
                {c.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="상품명">
          <input
            required
            maxLength={100}
            value={values.name}
            onChange={(e) => setValues({ ...values, name: e.target.value })}
            placeholder="예: 프리미엄 A4 복사용지"
          />
        </Field>
        <Field label="판매 단가 (원)" hint={preview ?? '0 이상의 정수 · 쉼표 없이 입력하세요.'}>
          <input
            required
            inputMode="numeric"
            type="text"
            value={values.price}
            onChange={(e) => setValues({ ...values, price: e.target.value })}
            placeholder="예: 18500"
          />
        </Field>
        <Field label="상품 설명 (선택)">
          <textarea
            rows={4}
            value={values.description}
            onChange={(e) => setValues({ ...values, description: e.target.value })}
            placeholder="규격, 포장 단위 등 주문에 필요한 정보"
          />
        </Field>
      </fieldset>
      <div className="actions align-end">
        {uncertain ? (
          <Button type="button" variant="secondary" onClick={() => onInspect(values.name)}>
            목록에서 등록 여부 확인
          </Button>
        ) : (
          <Button disabled={busy || directory.companies.isPending || directory.companies.isError}>
            <Plus size={16} />
            {busy ? '등록 중…' : '상품 등록 완료'}
          </Button>
        )}
      </div>
    </form>
  )
}
