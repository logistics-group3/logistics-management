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
          <span className="eyebrow">READY TO DELIVER</span>
          <h2>{directory.hubName(user.hubId!)} 배송 담당자입니다.</h2>
          <p>배정된 구간에서 출발·도착을 처리할 수 있습니다.</p>
        </div>
        <Link className="text-link" to="/deliveries">
          <Truck size={17} />내 배송 확인
          <ArrowRight size={15} />
        </Link>
      </div>
    )
  return (
    <section className="setup-guide" aria-label="주문 시작 안내">
      <div>
        <span className="eyebrow">GET STARTED</span>
        <h2>{owned.length ? '담당 업체가 연결되었습니다.' : '내 업체 등록부터 시작하세요.'}</h2>
        <p>
          {owned.length
            ? `${owned.map((c) => c.name).join(', ')}의 주문을 만들 수 있습니다.`
            : '업체를 등록하면 본인이 담당자로 연결되어 주문을 생성할 수 있습니다.'}
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
