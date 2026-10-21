import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Save, Truck } from 'lucide-react'
import { useAuth } from '../app/auth'
import { api } from '../shared/api'
import { deliveryManagerRequest } from '../shared/catalog-rules'
import { useDirectory } from '../shared/queries'
import type { User } from '../shared/types'
import { ErrorNotice, Field } from '../shared/ui'
import { Button } from '../components/ui/button'
export function DeliveryManagerForm({ target }: { target: User }) {
  const { user, notify, refreshUser } = useAuth()
  const directory = useDirectory()
  const cache = useQueryClient()
  const [assigned, setAssigned] = useState(target.isDeliveryManager)
  const [hub, setHub] = useState(target.hubId ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const inFlight = useRef(false)
  if (user?.role !== 'MASTER') return null
  const changed = target.isDeliveryManager !== assigned || (assigned && target.hubId !== hub)
  return (
    <section className="assignment-section">
      <h3>
        <Truck size={18} />
        배송 담당자 설정
      </h3>
      <p className="muted small">
        {target.name} ({target.username}) 사용자의 담당 허브를 설정합니다. 계정 역할은 변경하지
        않습니다.
      </p>
      <form
        onSubmit={async (event) => {
          event.preventDefault()
          if (inFlight.current || !changed) return
          setError(undefined)
          let body
          try {
            body = deliveryManagerRequest(assigned, hub)
          } catch (value) {
            setError(value)
            return
          }
          inFlight.current = true
          setBusy(true)
          try {
            await api(`/users/${target.userId}/role`, { method: 'PATCH', body })
            if (target.userId === user.userId) await refreshUser()
            await cache.invalidateQueries({ queryKey: ['users'] })
            notify(
              assigned
                ? '배송 담당자 지정이 저장되었습니다.'
                : '배송 담당자 지정이 해제되었습니다.',
            )
          } catch (value) {
            setError(value)
          } finally {
            inFlight.current = false
            setBusy(false)
          }
        }}
      >
        <ErrorNotice error={error} />
        <ErrorNotice error={directory.hubs.error} retry={() => void directory.hubs.refetch()} />
        <fieldset disabled={busy}>
          <label className="check-field">
            <input
              type="checkbox"
              checked={assigned}
              onChange={(e) => setAssigned(e.target.checked)}
            />
            <span>배송 담당자로 지정</span>
          </label>
          {assigned && (
            <Field label="담당 허브">
              <select required value={hub} onChange={(e) => setHub(e.target.value)}>
                <option value="">허브 선택</option>
                {directory.hubs.data?.map((h) => (
                  <option value={h.hubId} key={h.hubId}>
                    {h.name}
                  </option>
                ))}
              </select>
            </Field>
          )}
        </fieldset>
        <div className="notice">
          {assigned
            ? '지정하면 사용자 소속도 선택한 허브로 변경되며, 이후 배송 생성 시 해당 허브의 자동 배정 후보가 됩니다.'
            : '지정을 해제해도 기존 배송 구간은 자동 재배정되지 않습니다. 소속명은 서버 정책에 따라 유지됩니다.'}{' '}
          기존 배송의 담당자 변경은 별도 작업입니다.
        </div>
        <div className="actions align-end">
          <Button disabled={!changed || busy || (assigned && directory.hubs.isError)}>
            <Save size={16} />
            {busy ? '저장 중…' : '배송 담당 설정 저장'}
          </Button>
        </div>
      </form>
    </section>
  )
}
