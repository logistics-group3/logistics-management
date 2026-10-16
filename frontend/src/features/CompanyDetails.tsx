import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, Pencil, Plus } from 'lucide-react'
import { useAuth } from '../app/auth'
import { canManageCompany } from '../shared/catalog-rules'
import { useDirectory, useUsersDirectory } from '../shared/queries'
import type { Company } from '../shared/types'
import { DetailField } from '../shared/ui'
import { Button } from '../components/ui/button'
import { CompanyForm } from './CompanyForm'
export function CompanyDetails({
  company,
  onSaved,
  onInspect,
}: {
  company: Company
  onSaved: (company: Company) => void
  onInspect: (name: string) => void
}) {
  const { user } = useAuth()
  const directory = useDirectory()
  const users = useUsersDirectory(user?.role === 'MASTER')
  const [editing, setEditing] = useState(false)
  const manager =
    company.managerId === user?.userId
      ? user
      : users.data?.find((u) => u.userId === company.managerId)
  if (editing)
    return (
      <>
        <div className="section-heading">
          <h3>업체 정보 · 담당자 변경</h3>
          <Button variant="ghost" onClick={() => setEditing(false)}>
            상세로 돌아가기
          </Button>
        </div>
        <CompanyForm
          company={company}
          onInspect={onInspect}
          onSaved={(result) => {
            setEditing(false)
            onSaved(result)
          }}
        />
      </>
    )
  return (
    <>
      <dl className="detail-list">
        <DetailField label="업체명">{company.name}</DetailField>
        <DetailField label="관리 허브">{directory.hubName(company.hubId)}</DetailField>
        <DetailField label="주소">{company.address}</DetailField>
        <DetailField label="업체 담당자">
          {manager
            ? `${manager.name} (${manager.username})${manager.userId === user?.userId ? ' · 나' : ''}`
            : '다른 업체 담당자'}
          <small className="subline mono">{company.managerId}</small>
        </DetailField>
        <DetailField label="업체 ID">
          <code>{company.companyId}</code>
        </DetailField>
      </dl>
      {canManageCompany(user!, company) && (
        <div className="catalog-detail-actions">
          <Button variant="secondary" onClick={() => setEditing(true)}>
            <Pencil size={15} />
            {user?.role === 'MASTER' ? '업체 정보·담당자 변경' : '업체 정보 수정'}
          </Button>
          <Button asChild variant="secondary">
            <Link to={`/products?create=1&companyId=${company.companyId}`}>
              <Plus size={15} />이 업체에 상품 등록
            </Link>
          </Button>
          <Button asChild>
            <Link to="/orders/new">
              주문 등록으로
              <ArrowRight size={15} />
            </Link>
          </Button>
        </div>
      )}
    </>
  )
}
