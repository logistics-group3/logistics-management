import { Link } from 'react-router-dom'
import { ArrowRight, Check, Building2, Package, Truck } from 'lucide-react'
import { useAuth } from '../app/auth'
import { useDirectory } from '../shared/queries'
export function SetupGuide() {
  const { user } = useAuth()
  const directory = useDirectory()
  if (user?.role === 'MASTER' || directory.companies.isPending || directory.companies.isError)
    return null
  const owned = directory.companies.data?.filter((c) => c.managerId === user?.userId) ?? []
  if (user?.isDeliveryManager && !owned.length)
    return (
      <div className="setup-guide">
        <div>
          <h2>담당 허브: {directory.hubName(user.hubId!)}</h2>
          <p>본인 배정 구간만 출발·도착 처리 가능</p>
        </div>
        <Link className="text-link" to="/deliveries">
          <Truck size={17} />
          배정 배송 확인하기
          <ArrowRight size={15} />
        </Link>
      </div>
    )
  return (
    <section className="setup-guide" aria-label="주문 시작 안내">
      <div>
        <h2>{owned.length ? '담당 업체' : '담당 업체 등록 필요'}</h2>
        <p>
          {owned.length
            ? owned.map((c) => c.name).join(', ')
            : '주문 등록 전 담당 업체를 등록하세요.'}
        </p>
      </div>
      <div className="setup-steps">
        <Link to={owned.length ? '/companies' : '/companies?create=1'}>
          <span>{owned.length ? <Check size={17} /> : <Building2 size={17} />}</span>
          <strong>{owned.length ? '담당 업체 확인' : '01 업체 등록'}</strong>
          <ArrowRight size={14} />
        </Link>
        <Link to="/products">
          <span>
            <Package size={17} />
          </span>
          <strong>02 공급 상품 확인</strong>
          <ArrowRight size={14} />
        </Link>
        {owned.length ? (
          <Link to="/orders/new">
            <span>
              <Truck size={17} />
            </span>
            <strong>03 주문 생성</strong>
            <ArrowRight size={14} />
          </Link>
        ) : (
          <div className="setup-inactive">
            <span>
              <Truck size={17} />
            </span>
            <strong>03 업체 등록 후 주문</strong>
          </div>
        )}
      </div>
    </section>
  )
}
