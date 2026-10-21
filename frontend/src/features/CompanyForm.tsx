import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Check, RefreshCw, Save } from 'lucide-react'
import { useAuth } from '../app/auth'
import { api, ApiError } from '../shared/api'
import { buildCompanyRequest } from '../shared/catalog-rules'
import { useDirectory, useUsersDirectory } from '../shared/queries'
import type { Company } from '../shared/types'
import { ErrorNotice, Field } from '../shared/ui'
import { Button } from '../components/ui/button'
export function CompanyForm({
  company,
  onSaved,
  onInspect,
}: {
  company?: Company
  onSaved: (company: Company) => void
  onInspect: (name: string) => void
}) {
  const { user, notify } = useAuth()
  const master = user!.role === 'MASTER'
  const directory = useDirectory()
  const users = useUsersDirectory(master)
  const [values, setValues] = useState({
    name: company?.name ?? '',
    address: company?.address ?? '',
    hubId: company?.hubId ?? '',
    managerId: company?.managerId ?? user!.userId,
  })
  const [search, setSearch] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [uncertain, setUncertain] = useState(false)
  const inFlight = useRef(false)
  const cache = useQueryClient()
  const manager = users.data?.find((u) => u.userId === values.managerId)
  return (
    <form
      onSubmit={async (event) => {
        event.preventDefault()
        if (inFlight.current || uncertain) return
        setError(undefined)
        let body
        try {
          body = buildCompanyRequest(values, user!)
        } catch (value) {
          setError(value)
          return
        }
        inFlight.current = true
        setBusy(true)
        try {
          const result = await api<Company>(
            company ? `/companies/${company.companyId}` : '/companies',
            { method: company ? 'PATCH' : 'POST', body },
          )
          if (!result?.companyId)
            throw new ApiError(0, 'INVALID_RESPONSE', '업체 저장 결과를 확인할 수 없습니다.')
          await Promise.all([
            cache.invalidateQueries({ queryKey: ['companies'] }),
            cache.invalidateQueries({ queryKey: ['directory', 'companies'] }),
            cache.invalidateQueries({ queryKey: ['products'] }),
          ])
          notify(company ? '업체 정보가 저장되었습니다.' : '업체가 등록되었습니다.')
          onSaved(result)
        } catch (value) {
          setError(value)
          if (!company && value instanceof ApiError && (value.status === 0 || value.status >= 500))
            setUncertain(true)
        } finally {
          inFlight.current = false
          setBusy(false)
        }
      }}
    >
      {!master && <div className="notice">업체 담당자는 로그인한 사용자로 지정됩니다.</div>}
      <ErrorNotice
        error={error}
        help={
          uncertain
            ? '등록이 완료되었을 수도 있습니다. 중복 등록을 피하도록 업체 목록에서 먼저 확인해 주세요.'
            : undefined
        }
      />
      <ErrorNotice error={directory.hubs.error} retry={() => void directory.hubs.refetch()} />
      {master && <ErrorNotice error={users.error} retry={() => void users.refetch()} />}
      <fieldset disabled={busy || uncertain}>
        <Field label="업체명">
          <input
            required
            maxLength={100}
            value={values.name}
            onChange={(e) => setValues({ ...values, name: e.target.value })}
            placeholder="예: 한빛 리테일 강남점"
          />
        </Field>
        <Field label="관리 허브" hint="집하·배송을 담당할 허브">
          <select
            required
            value={values.hubId}
            onChange={(e) => setValues({ ...values, hubId: e.target.value })}
          >
            <option value="">허브 선택</option>
            {directory.hubs.data?.map((h) => (
              <option value={h.hubId} key={h.hubId}>
                {h.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="업체 주소" hint="집하 또는 배송 주소로 사용됩니다.">
          <input
            required
            maxLength={255}
            value={values.address}
            onChange={(e) => setValues({ ...values, address: e.target.value })}
            placeholder="도로명 주소와 상세 주소"
          />
        </Field>
        <section className="assignment-section">
          <h3>업체 담당자</h3>
          {master ? (
            <>
              <div className="actions align-end">
                <Button
                  type="button"
                  variant="ghost"
                  disabled={users.isFetching}
                  onClick={() => void users.refetch()}
                >
                  <RefreshCw size={14} />
                  사용자 목록 새로고침
                </Button>
              </div>
              <Field label="담당자 이름·아이디 검색">
                <input
                  type="search"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="이름 또는 로그인 아이디"
                />
              </Field>
              <Field label="업체 담당자">
                <select
                  required
                  value={values.managerId}
                  onChange={(e) => setValues({ ...values, managerId: e.target.value })}
                >
                  <option value="">담당자 선택</option>
                  {users.data
                    ?.filter(
                      (u) =>
                        u.name.includes(search) ||
                        u.username.includes(search) ||
                        u.userId === values.managerId,
                    )
                    .map((u) => (
                      <option value={u.userId} key={u.userId}>
                        {u.name} ({u.username})
                      </option>
                    ))}
                </select>
              </Field>
              {company && company.managerId !== values.managerId && (
                <div className="notice warning">
                  이 업체의 관리 권한을 {manager?.name ?? '선택한 사용자'}에게 이전합니다. 기존
                  주문의 주문자는 변경되지 않습니다.
                </div>
              )}
            </>
          ) : (
            <div className="assigned-person">
              <span className="account-avatar">{user!.name.slice(0, 1)}</span>
              <div>
                <strong>{user!.name} (나)</strong>
                <span>{user!.username}</span>
              </div>
              <Check size={18} />
              <p>담당자 변경 권한: 관리자</p>
            </div>
          )}
        </section>
      </fieldset>
      <div className="actions align-end">
        {uncertain ? (
          <Button type="button" variant="secondary" onClick={() => onInspect(values.name)}>
            목록에서 등록 여부 확인
          </Button>
        ) : (
          <Button
            disabled={
              busy || directory.hubs.isPending || (master && (users.isPending || users.isError))
            }
          >
            <Save size={16} />
            {busy ? '저장 중…' : company ? '업체 변경 저장' : '업체 등록'}
          </Button>
        )}
      </div>
    </form>
  )
}
