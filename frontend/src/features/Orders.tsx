import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ArrowRight, Package, Plus, RefreshCw, SlidersHorizontal } from 'lucide-react'
import { api, params } from '../shared/api'
import type { Order, Page } from '../shared/types'
import { useDirectory } from '../shared/queries'
import { dateTime, money, shortId } from '../shared/format'
import { Badge, Empty, ErrorNotice, Field, Loading, PageTitle, Pagination } from '../shared/ui'
import { Button } from '../components/ui/button'
import { Dialog } from '../components/ui/dialog'
import { OrderDetail } from './OrderDetail'
import { CreateOrder } from './CreateOrder'

export function Orders() {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [filters, setFilters] = useState({
    status: '',
    supplierCompanyId: '',
    receiverCompanyId: '',
    startDate: '',
    endDate: '',
    sort: 'createdAt,desc',
  })
  const directory = useDirectory()
  const query = params({ ...filters, page, size })
  const orders = useQuery({
    queryKey: ['orders', query],
    queryFn: ({ signal }) => api<Page<Order>>(`/orders?${query}`, { signal }),
  })
  const selected = useLocation().pathname.split('/')[2]
  const navigate = useNavigate()
  const setFilter = (key: keyof typeof filters, value: string) => {
    setFilters({ ...filters, [key]: value })
    setPage(0)
  }
  return (
    <>
      <PageTitle
        eyebrow="ORDER MANAGEMENT"
        title="주문 관리"
        description="주문의 접수부터 변경, 취소까지 한곳에서 관리합니다."
        actions={
          <>
            <Button
              variant="secondary"
              onClick={() => void orders.refetch()}
              aria-label="주문 목록 새로고침"
            >
              <RefreshCw size={16} />
              새로고침
            </Button>
            <Button asChild>
              <Link to="/orders/new">
                <Plus size={17} />새 주문 등록
              </Link>
            </Button>
          </>
        }
      />
      <section className="panel">
        <div className="tabs" role="group" aria-label="주문 상태 필터">
          {[
            ['', '전체 주문'],
            ['ORDERED', '주문 완료'],
            ['CANCELED', '취소'],
          ].map(([value, label]) => (
            <button
              key={value}
              className={filters.status === value ? 'active' : ''}
              aria-pressed={filters.status === value}
              onClick={() => setFilter('status', value)}
            >
              {label}
              {value === filters.status && orders.data && <span>{orders.data.totalElements}</span>}
            </button>
          ))}
        </div>
        <div className="filters">
          <div className="filter-label">
            <SlidersHorizontal size={16} />
            조회 조건
          </div>
          <Field label="공급업체">
            <select
              value={filters.supplierCompanyId}
              onChange={(e) => setFilter('supplierCompanyId', e.target.value)}
            >
              <option value="">전체 공급업체</option>
              {directory.companies.data?.map((c) => (
                <option value={c.companyId} key={c.companyId}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="수령업체">
            <select
              value={filters.receiverCompanyId}
              onChange={(e) => setFilter('receiverCompanyId', e.target.value)}
            >
              <option value="">전체 수령업체</option>
              {directory.companies.data?.map((c) => (
                <option value={c.companyId} key={c.companyId}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="시작일">
            <input
              type="date"
              value={filters.startDate}
              max={filters.endDate || undefined}
              onChange={(e) => setFilter('startDate', e.target.value)}
            />
          </Field>
          <Field label="종료일">
            <input
              type="date"
              value={filters.endDate}
              min={filters.startDate || undefined}
              onChange={(e) => setFilter('endDate', e.target.value)}
            />
          </Field>
          <Button
            variant="ghost"
            onClick={() => {
              setFilters({
                status: '',
                supplierCompanyId: '',
                receiverCompanyId: '',
                startDate: '',
                endDate: '',
                sort: 'createdAt,desc',
              })
              setPage(0)
            }}
          >
            초기화
          </Button>
        </div>
        <div className="table-toolbar">
          <span>
            주문 목록 <strong>{orders.data?.totalElements ?? '—'}</strong>
          </span>
          <select
            aria-label="주문 정렬"
            value={filters.sort}
            onChange={(e) => setFilter('sort', e.target.value)}
          >
            <option value="createdAt,desc">최근 주문순</option>
            <option value="createdAt,asc">오래된 주문순</option>
            <option value="quantity,desc">수량 많은 순</option>
          </select>
        </div>
        <ErrorNotice error={orders.error} retry={() => void orders.refetch()} />
        {orders.isPending ? (
          <Loading />
        ) : orders.data?.content.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>주문 번호 / 접수일</th>
                  <th>상품명</th>
                  <th>공급업체 → 수령업체</th>
                  <th className="numeric">수량</th>
                  <th className="numeric">주문 금액</th>
                  <th>주문 상태</th>
                  <th>
                    <span className="sr-only">상세 보기</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {orders.data.content.map((o) => (
                  <tr key={o.orderId}>
                    <td>
                      <Link className="row-link mono" to={`/orders/${o.orderId}`}>
                        ORD-{shortId(o.orderId)}
                      </Link>
                      <small className="subline">{dateTime(o.createdAt)}</small>
                    </td>
                    <td>
                      <div className="product-cell">
                        <span className="product-icon">
                          <Package size={17} />
                        </span>
                        <strong>{o.productName}</strong>
                      </div>
                    </td>
                    <td>
                      <span className="subline">{directory.companyName(o.supplierCompanyId)}</span>
                      <span className="company-destination">
                        <ArrowRight size={12} />
                        {directory.companyName(o.receiverCompanyId)}
                      </span>
                    </td>
                    <td className="numeric">{o.quantity.toLocaleString()}</td>
                    <td className="numeric strong">{money(o.totalPrice)}</td>
                    <td>
                      <Badge status={o.status} />
                    </td>
                    <td>
                      <Link
                        className="table-action"
                        aria-label={`주문 ${shortId(o.orderId)} 상세`}
                        to={`/orders/${o.orderId}`}
                      >
                        <ArrowRight size={17} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          !orders.isError && (
            <Empty text="조회된 주문이 없습니다.">
              <Button asChild>
                <Link to="/orders/new">첫 주문 등록</Link>
              </Button>
            </Empty>
          )
        )}
        <Pagination data={orders.data} page={page} onPage={setPage} size={size} onSize={setSize} />
      </section>
      <p className="table-footnote">
        주문 상태와 배송 진행 상태는 별도로 관리됩니다. 배송 현황은 주문 상세에서 확인할 수
        있습니다.
      </p>
      {selected === 'new' ? (
        <Dialog
          open
          onClose={() => navigate('/orders')}
          title="새 주문 등록"
          description="공급업체의 상품을 선택하고 수령 정보를 확인하세요."
          sheet
        >
          <CreateOrder />
        </Dialog>
      ) : (
        selected && (
          <Dialog
            open
            onClose={() => navigate('/orders')}
            title="주문 상세"
            description={`ORD-${shortId(selected)}`}
            sheet
          >
            <OrderDetail id={selected} key={selected} />
          </Dialog>
        )
      )}
    </>
  )
}
