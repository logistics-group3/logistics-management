import { cloneElement, useId } from 'react'
import type { ReactElement, ReactNode } from 'react'
import { AlertCircle, ArrowLeft, ArrowRight, Inbox, LoaderCircle, RefreshCw } from 'lucide-react'
import { ApiError, errorHelp } from './api'
import { labels } from './format'
import { Button } from '../components/ui/button'
import type { Page } from './types'
export function Badge({ status }: { status: string }) {
  return (
    <span className={`badge badge-${status.toLowerCase()}`}>
      <span />
      {labels[status] ?? status}
    </span>
  )
}
export function ErrorNotice({
  error,
  retry,
  help,
}: {
  error: unknown
  retry?: () => void
  help?: string
}) {
  if (!error) return null
  return (
    <div className="notice error" role="alert">
      <AlertCircle size={18} />
      <div>
        <strong>{error instanceof Error ? error.message : '요청을 처리하지 못했습니다.'}</strong>
        <p>{help ?? errorHelp(error)}</p>
        {error instanceof ApiError && (
          <details>
            <summary>오류 정보</summary>
            <code>
              HTTP {error.status || '응답 없음'} · {error.code}
            </code>
          </details>
        )}
        {retry && (
          <Button variant="secondary" onClick={retry}>
            <RefreshCw size={14} />
            다시 조회
          </Button>
        )}
      </div>
    </div>
  )
}
export function Loading() {
  return (
    <div className="empty" role="status">
      <LoaderCircle className="spin" size={24} />
      <p>데이터를 불러오고 있습니다.</p>
    </div>
  )
}
export function Empty({
  text = '조건에 맞는 데이터가 없습니다.',
  children,
}: {
  text?: string
  children?: ReactNode
}) {
  return (
    <div className="empty">
      <Inbox size={30} />
      <h3>{text}</h3>
      <p>검색 조건을 바꾸거나 새 데이터를 등록해 보세요.</p>
      {children}
    </div>
  )
}
export function PageTitle({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow: string
  title: string
  description: string
  actions?: ReactNode
}) {
  return (
    <div className="page-heading">
      <div>
        <span className="eyebrow">{eyebrow}</span>
        <h1 id="page-title" tabIndex={-1}>
          {title}
        </h1>
        <p>{description}</p>
      </div>
      <div className="actions">{actions}</div>
    </div>
  )
}
export function Field({
  label,
  children,
  hint,
}: {
  label: string
  children: ReactNode
  hint?: string
}) {
  const id = useId()
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {cloneElement(children as ReactElement<{ id?: string; 'aria-describedby'?: string }>, {
        id,
        'aria-describedby': hint ? `${id}-hint` : undefined,
      })}
      {hint && <small id={`${id}-hint`}>{hint}</small>}
    </div>
  )
}
export function Pagination({
  data,
  page,
  onPage,
  size,
  onSize,
}: {
  data?: Page<unknown>
  page: number
  onPage: (page: number) => void
  size: number
  onSize: (size: number) => void
}) {
  return (
    <div className="pagination">
      <span>
        전체 <strong>{data?.totalElements.toLocaleString() ?? '—'}</strong>건
      </span>
      <div className="actions">
        <label className="page-size">
          페이지당{' '}
          <select
            aria-label="페이지당 건수"
            value={size}
            onChange={(e) => {
              onSize(Number(e.target.value))
              onPage(0)
            }}
          >
            {[10, 30, 50].map((v) => (
              <option key={v} value={v}>
                {v}건
              </option>
            ))}
          </select>
        </label>
        <Button
          variant="ghost"
          aria-label="이전 페이지"
          disabled={page === 0}
          onClick={() => onPage(page - 1)}
        >
          <ArrowLeft size={16} />
        </Button>
        <span>
          {page + 1} / {Math.max(1, data?.totalPages ?? 1)}
        </span>
        <Button
          variant="ghost"
          aria-label="다음 페이지"
          disabled={!data || page + 1 >= data.totalPages}
          onClick={() => onPage(page + 1)}
        >
          <ArrowRight size={16} />
        </Button>
      </div>
    </div>
  )
}
export function DetailField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="detail-field">
      <dt>{label}</dt>
      <dd>{children || '—'}</dd>
    </div>
  )
}
