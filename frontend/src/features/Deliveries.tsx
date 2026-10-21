import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowRight, ListFilter, RefreshCw, Truck } from 'lucide-react'
import { api, params } from '../shared/api'
import { labels, dateTime, shortId } from '../shared/format'
import { useDirectory } from '../shared/queries'
import { Badge, Empty, ErrorNotice, Field, Loading, PageTitle, Pagination } from '../shared/ui'
import { DeliverySheet } from './DeliverySheet'
import type { Delivery, Page } from '../shared/types'
import { Button } from '../components/ui/button'
import { Dialog } from '../components/ui/dialog'
export function Deliveries() {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [searchParams, setSearchParams] = useSearchParams()
  const status = searchParams.get('status') ?? ''
  const setStatus = (value: string) => {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous)
      if (value) next.set('status', value)
      else next.delete('status')
      return next
    })
  }
  const [hub, setHub] = useState('')
  const directory = useDirectory()
  const query = params({ page, size, status, destinationHubId: hub, sort: 'createdAt,desc' })
  const deliveries = useQuery({
    queryKey: ['deliveries', query],
    queryFn: ({ signal }) => api<Page<Delivery>>(`/deliveries?${query}`, { signal }),
  })
  const selected = useLocation().pathname.split('/')[2]
  const navigate = useNavigate()
  return (
    <>
      <PageTitle
        title="배송 관리"
        actions={
          <Button variant="secondary" onClick={() => void deliveries.refetch()}>
            <RefreshCw size={16} />
            새로고침
          </Button>
        }
      />
      <section className="panel delivery-list-panel">
        <div className="list-section-heading">
          <span>
            <Truck size={17} />
            배송 조회
          </span>
          <span className="small muted">상태별 조회 · 담당 구간 처리</span>
        </div>
        <div className="filters">
          <div className="filter-label">
            <ListFilter size={15} />
            조회 조건
          </div>
          <Field label="배송 상태">
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value)
                setPage(0)
              }}
            >
              <option value="">전체 상태</option>
              {[
                'PICKUP_WAITING',
                'PICKING_UP',
                'HUB_WAITING',
                'HUB_MOVING',
                'DEST_HUB_ARRIVED',
                'OUT_FOR_DELIVERY',
                'DELIVERED',
                'CANCELED',
              ].map((s) => (
                <option key={s} value={s}>
                  {labels[s]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="도착 허브">
            <select
              value={hub}
              onChange={(e) => {
                setHub(e.target.value)
                setPage(0)
              }}
            >
              <option value="">전체 허브</option>
              {directory.hubs.data?.map((h) => (
                <option value={h.hubId} key={h.hubId}>
                  {h.name}
                </option>
              ))}
            </select>
          </Field>
          <Button
            variant="ghost"
            onClick={() => {
              setStatus('')
              setHub('')
              setPage(0)
            }}
          >
            초기화
          </Button>
        </div>
        <div className="table-toolbar">
          <span>
            배송 목록 <strong>{deliveries.data?.totalElements ?? '—'}</strong>
          </span>
          <span className="small muted">최근 생성순</span>
        </div>
        <ErrorNotice error={deliveries.error} retry={() => void deliveries.refetch()} />
        {deliveries.isPending ? (
          <Loading />
        ) : deliveries.data?.content.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>배송 번호 / 생성일</th>
                  <th>공급업체 → 수령업체</th>
                  <th>출발 → 도착 허브</th>
                  <th>수령인</th>
                  <th>배송 상태</th>
                  <th>
                    <span className="sr-only">상세 보기</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {deliveries.data.content.map((d) => (
                  <tr key={d.deliveryId}>
                    <td>
                      <Link
                        className="row-link mono"
                        to={{
                          pathname: `/deliveries/${d.deliveryId}`,
                          search: searchParams.toString(),
                        }}
                      >
                        DLV-{shortId(d.deliveryId)}
                      </Link>
                      <span className="subline">{dateTime(d.createdAt)}</span>
                    </td>
                    <td>
                      <span className="subline">{directory.companyName(d.supplierCompanyId)}</span>
                      <span className="company-destination">
                        <ArrowRight size={12} />
                        {directory.companyName(d.receiverCompanyId)}
                      </span>
                    </td>
                    <td>
                      <span className="subline">{directory.hubName(d.departureHubId)}</span>
                      <span className="company-destination">
                        <ArrowRight size={12} />
                        {directory.hubName(d.destinationHubId)}
                      </span>
                    </td>
                    <td>{d.recipientName}</td>
                    <td>
                      <Badge status={d.status} />
                    </td>
                    <td>
                      <Link
                        className="table-action"
                        aria-label={`배송 ${shortId(d.deliveryId)} 상세`}
                        to={{
                          pathname: `/deliveries/${d.deliveryId}`,
                          search: searchParams.toString(),
                        }}
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
          !deliveries.isError && <Empty text="조회된 배송이 없습니다." />
        )}
        <Pagination
          data={deliveries.data}
          page={page}
          onPage={setPage}
          size={size}
          onSize={setSize}
        />
      </section>
      <p className="table-footnote">
        구간 출발·도착은 해당 배송 담당자 또는 관리자가 처리할 수 있습니다.
      </p>
      {selected && (
        <Dialog
          open
          onClose={() => navigate({ pathname: '/deliveries', search: searchParams.toString() })}
          title="배송 상세"
          description={`DLV-${shortId(selected)}`}
          sheet
        >
          <DeliverySheet key={selected} id={selected} />
        </Dialog>
      )}
    </>
  )
}
