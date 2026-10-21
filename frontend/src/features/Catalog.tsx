import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Building2, MapPin, Plus, Search, Users } from 'lucide-react'
import { api, params } from '../shared/api'
import { useAuth } from '../app/auth'
import { useDirectory } from '../shared/queries'
import { dateTime, money } from '../shared/format'
import type { Company, Hub, Page, Product, User } from '../shared/types'
import { Badge, Empty, ErrorNotice, Loading, PageTitle, Pagination } from '../shared/ui'
import { Button } from '../components/ui/button'
import { Dialog } from '../components/ui/dialog'
import { CatalogDetails } from './CatalogDetails'
import { CompanyForm } from './CompanyForm'
import { ProductForm } from './ProductForm'
type Kind = 'companies' | 'products' | 'hubs' | 'users'
type Item = Company | Product | Hub | User
const config = {
  companies: {
    title: '업체 관리',
    search: '업체명',
    key: 'companyId',
  },
  products: {
    title: '상품 관리',
    search: '상품명',
    key: 'productId',
  },
  hubs: {
    title: '허브 관리',
    search: '허브명 또는 주소',
    key: 'hubId',
  },
  users: {
    title: '사용자 관리',
    search: '사용자 이름',
    key: 'userId',
  },
}
const itemValue = (item: Item, key: string) =>
  String((item as unknown as Record<string, unknown>)[key] ?? '')
export function Catalog({ kind }: { kind: Kind }) {
  const { user } = useAuth()
  const info = config[kind]
  const directory = useDirectory()
  const [searchParams, setSearchParams] = useSearchParams()
  const creating =
    searchParams.get('create') === '1' && (kind === 'companies' || kind === 'products')
  const [search, setSearch] = useState('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [selected, setSelected] = useState<Item | null>(null)
  const onlySelf = kind === 'users' && user?.role !== 'MASTER'
  const query = params({ [kind === 'hubs' ? 'keyword' : 'name']: keyword, page, size })
  const data = useQuery({
    queryKey: [kind, 'catalog', query],
    queryFn: ({ signal }) => api<Page<Item>>(`/${kind}?${query}`, { signal }),
    enabled: !onlySelf,
  })
  const detailPath = selected
    ? kind === 'products'
      ? `/companies/${(selected as Product).companyId}/products/${(selected as Product).productId}`
      : `/${kind}/${itemValue(selected, info.key)}`
    : ''
  const detail = useQuery({
    queryKey: [kind, 'detail', detailPath],
    queryFn: ({ signal }) => api<Item>(detailPath, { signal }),
    enabled: Boolean(selected),
  })
  const created = (item: Item) => {
    setSearchParams({})
    setSelected(item)
  }
  const inspect = (name: string) => {
    setSearchParams({})
    setSelected(null)
    setSearch(name)
    setKeyword(name)
    setPage(0)
    void data.refetch()
  }
  return (
    <>
      <PageTitle
        title={info.title}
        actions={
          kind === 'companies' || kind === 'products' ? (
            <Button
              onClick={() => {
                setSelected(null)
                setSearchParams({ create: '1' })
              }}
            >
              <Plus size={16} />
              {kind === 'companies' ? '업체 등록' : '상품 등록'}
            </Button>
          ) : undefined
        }
      />
      {onlySelf ? (
        <section className="panel profile-panel">
          <div className="detail-hero">
            <span className="detail-icon">
              <Users size={26} />
            </span>
            <div>
              <h2>내 프로필</h2>
              <p>전체 사용자 목록은 관리자에게 제공됩니다.</p>
            </div>
          </div>
          <CatalogDetails kind="users" item={user!} onSaved={setSelected} onInspect={inspect} />
        </section>
      ) : (
        <section className="panel">
          <div className="catalog-toolbar">
            <form
              className="search-form"
              onSubmit={(e) => {
                e.preventDefault()
                setKeyword(search.trim())
                setPage(0)
              }}
            >
              <label className="search-input">
                <Search size={17} />
                <input
                  type="search"
                  aria-label={`${info.search} 검색`}
                  placeholder={`${info.search} 검색`}
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
              </label>
              <Button type="submit" variant="secondary">
                검색
              </Button>
              {keyword && (
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => {
                    setSearch('')
                    setKeyword('')
                    setPage(0)
                  }}
                >
                  초기화
                </Button>
              )}
            </form>
            <span className="muted small">총 {data.data?.totalElements ?? '—'}건</span>
          </div>
          <ErrorNotice error={data.error} retry={() => void data.refetch()} />
          {data.isPending ? (
            <Loading />
          ) : data.data?.content.length ? (
            kind === 'hubs' ? (
              <div className="hub-grid">
                {(data.data.content as Hub[]).map((hub, i) => (
                  <button className="hub-card" onClick={() => setSelected(hub)} key={hub.hubId}>
                    <span className="hub-card-top">
                      <span className="hub-icon">
                        <Building2 size={24} />
                      </span>
                      <span className="mono">
                        HUB {String(page * size + i + 1).padStart(2, '0')}
                      </span>
                    </span>
                    <h3>{hub.name}</h3>
                    <p>
                      <MapPin size={14} />
                      {hub.address}
                    </p>
                    <span className="hub-coordinates">
                      {hub.latitude}° N · {hub.longitude}° E
                    </span>
                  </button>
                ))}
              </div>
            ) : (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>
                        {kind === 'companies'
                          ? '업체명'
                          : kind === 'products'
                            ? '상품명'
                            : '이름 / 아이디'}
                      </th>
                      <th>
                        {kind === 'companies'
                          ? '관리 허브'
                          : kind === 'products'
                            ? '공급업체'
                            : '소속'}
                      </th>
                      <th>
                        {kind === 'companies' ? '주소' : kind === 'products' ? '판매 단가' : '역할'}
                      </th>
                      <th>{kind === 'users' ? '배송 담당' : '등록일'}</th>
                      <th>상세</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.data.content.map((item) => (
                      <tr key={itemValue(item, info.key)}>
                        <td>
                          <button
                            className="row-link text-button"
                            onClick={() => setSelected(item)}
                          >
                            {itemValue(item, 'name')}
                          </button>
                          {kind === 'users' && (
                            <span className="subline">{(item as User).username}</span>
                          )}
                        </td>
                        <td>
                          {kind === 'companies'
                            ? directory.hubName((item as Company).hubId)
                            : kind === 'products'
                              ? directory.companyName((item as Product).companyId)
                              : (item as User).affiliationName}
                        </td>
                        <td>
                          {kind === 'companies' ? (
                            <span className="address-cell">{(item as Company).address}</span>
                          ) : kind === 'products' ? (
                            <span className="numeric strong">{money((item as Product).price)}</span>
                          ) : (
                            <Badge status={(item as User).role} />
                          )}
                        </td>
                        <td>
                          {kind === 'users' ? (
                            (item as User).isDeliveryManager ? (
                              <span className="text-teal">배송 담당자</span>
                            ) : (
                              '—'
                            )
                          ) : (
                            dateTime(itemValue(item, 'createdAt'))
                          )}
                        </td>
                        <td>
                          <Button
                            variant="ghost"
                            onClick={() => setSelected(item)}
                            aria-label={`${itemValue(item, 'name')} 상세`}
                          >
                            보기
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          ) : (
            !data.isError && <Empty />
          )}
          <Pagination data={data.data} page={page} onPage={setPage} size={size} onSize={setSize} />
        </section>
      )}
      <p className="table-footnote">
        {kind === 'companies'
          ? '일반 사용자는 업체 등록 시 본인이 담당자로 지정됩니다. 담당자 변경은 관리자만 할 수 있습니다.'
          : kind === 'products'
            ? '상품 등록은 해당 업체 담당자 또는 관리자가 할 수 있습니다.'
            : kind === 'users'
              ? '배송 담당자 지정과 소속 허브 변경은 관리자가 사용자 상세에서 처리합니다.'
              : '허브 정보를 확인하고 업체·배송의 관리 거점을 선택하세요.'}
      </p>
      <Dialog
        open={Boolean(selected) && !creating}
        onClose={() => setSelected(null)}
        title={`${info.title.replace(' 관리', '')} 상세`}
        description={selected ? itemValue(selected, 'name') : ''}
        sheet
      >
        {detail.isPending ? (
          <Loading />
        ) : detail.isError ? (
          <ErrorNotice error={detail.error} retry={() => void detail.refetch()} />
        ) : (
          detail.data && (
            <CatalogDetails
              kind={kind}
              item={detail.data}
              onSaved={setSelected}
              onInspect={inspect}
            />
          )
        )}
      </Dialog>
      <Dialog
        open={creating}
        onClose={() => setSearchParams({})}
        title={kind === 'companies' ? '업체 등록' : '상품 등록'}
        sheet
      >
        {creating &&
          (kind === 'companies' ? (
            <CompanyForm onSaved={created} onInspect={inspect} />
          ) : (
            <ProductForm
              companyId={searchParams.get('companyId') ?? ''}
              onSaved={created}
              onInspect={inspect}
            />
          ))}
      </Dialog>
    </>
  )
}
