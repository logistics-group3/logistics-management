import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Building2, RefreshCw } from 'lucide-react'
import { useAuth } from '../app/auth'
import { useDirectory } from '../shared/queries'
import { labels, money } from '../shared/format'
import type { Company, Hub, Product, User } from '../shared/types'
import { Badge, DetailField, ErrorNotice } from '../shared/ui'
import { Button } from '../components/ui/button'
import { CompanyDetails } from './CompanyDetails'
import { DeliveryManagerForm } from './DeliveryManagerForm'
export type CatalogKind = 'companies' | 'products' | 'hubs' | 'users'
export type CatalogItem = Company | Product | Hub | User
export function CatalogDetails({
  kind,
  item,
  onSaved,
  onInspect,
}: {
  kind: CatalogKind
  item: CatalogItem
  onSaved: (item: CatalogItem) => void
  onInspect: (name: string) => void
}) {
  const directory = useDirectory()
  const { user, refreshUser } = useAuth()
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<unknown>()
  if (kind === 'companies')
    return (
      <CompanyDetails
        key={(item as Company).companyId}
        company={item as Company}
        onSaved={onSaved}
        onInspect={onInspect}
      />
    )
  if (kind === 'products') {
    const p = item as Product
    return (
      <dl className="detail-list">
        <DetailField label="상품명">{p.name}</DetailField>
        <DetailField label="공급업체">{directory.companyName(p.companyId)}</DetailField>
        <DetailField label="단가">{money(p.price)}</DetailField>
        <DetailField label="상품 설명">{p.description}</DetailField>
        <DetailField label="상품 ID">
          <code>{p.productId}</code>
        </DetailField>
      </dl>
    )
  }
  if (kind === 'hubs') {
    const h = item as Hub
    return (
      <>
        <div className="hub-detail-visual">
          <Building2 size={54} />
          <span>{h.name}</span>
        </div>
        <dl className="detail-list">
          <DetailField label="허브명">{h.name}</DetailField>
          <DetailField label="주소">{h.address}</DetailField>
          <DetailField label="좌표">
            {h.latitude}, {h.longitude}
          </DetailField>
          <DetailField label="허브 ID">
            <code>{h.hubId}</code>
          </DetailField>
        </dl>
      </>
    )
  }
  const u = item as User
  return (
    <>
      <ErrorNotice error={error} />
      <dl className="detail-list">
        <DetailField label="이름 / 아이디">
          {u.name} / {u.username}
        </DetailField>
        <DetailField label="역할">
          <Badge status={u.role} />
        </DetailField>
        <DetailField label="소속">
          {labels[u.affiliationType]} · {u.affiliationName}
        </DetailField>
        <DetailField label="배송 담당">
          {u.isDeliveryManager ? `${directory.hubName(u.hubId!)} 담당자` : '미지정'}
        </DetailField>
        <DetailField label="Slack ID">{u.slackId}</DetailField>
        <DetailField label="사용자 ID">
          <code>{u.userId}</code>
        </DetailField>
      </dl>
      {user?.userId === u.userId && (
        <div className="catalog-detail-actions">
          <Button
            variant="secondary"
            disabled={refreshing}
            onClick={async () => {
              setRefreshing(true)
              setError(undefined)
              try {
                await refreshUser()
              } catch (value) {
                setError(value)
              } finally {
                setRefreshing(false)
              }
            }}
          >
            <RefreshCw size={15} />내 정보 새로고침
          </Button>
          <Button asChild>
            <Link to="/companies?create=1">내 업체 등록하기</Link>
          </Button>
        </div>
      )}
      {user?.role === 'MASTER' && (
        <DeliveryManagerForm key={`${u.userId}:${u.isDeliveryManager}:${u.hubId}`} target={u} />
      )}
    </>
  )
}
