import { isLosslessNumber, parse, stringify } from 'lossless-json'

let token: string | null = null
let onExpired = () => {}
export function configureAuth(value: string | null, expired?: () => void) {
  token = value
  if (expired) onExpired = expired
}
export class ApiError extends Error {
  status: number
  code: string
  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}
export function parseResponse(text: string): unknown {
  // Note: Java long 원화 금액은 JSON.parse에서 이미 반올림될 수 있어 원문 숫자를 문자열로 보존한다.
  return parse(text, (key, value) => {
    if (!isLosslessNumber(value)) return value
    if (['price', 'unitPrice', 'totalPrice'].includes(key)) return value.value
    const number = Number(value.value)
    return Number.isInteger(number) && !Number.isSafeInteger(number) ? value.value : number
  })
}
export function serializeRequest(value: unknown) {
  return stringify(value)
}
export async function api<T>(
  path: string,
  options: { method?: string; body?: unknown; key?: string; signal?: AbortSignal } = {},
): Promise<T> {
  const sentToken = token
  let response: Response
  try {
    response = await fetch(`/api/v1${path}`, {
      method: options.method ?? 'GET',
      headers: {
        Accept: 'application/json',
        ...(sentToken ? { Authorization: `Bearer ${sentToken}` } : {}),
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...(options.key ? { 'Idempotency-Key': options.key } : {}),
      },
      body: options.body ? serializeRequest(options.body) : undefined,
      signal: options.signal
        ? AbortSignal.any([options.signal, AbortSignal.timeout(20000)])
        : AbortSignal.timeout(20000),
    })
  } catch (error) {
    if (options.signal?.aborted) throw error
    throw new ApiError(
      0,
      'NETWORK_ERROR',
      '서버 응답을 확인하지 못했습니다. 연결 상태를 확인해 주세요.',
    )
  }
  const raw = await response.text()
  let envelope: { data?: T; code?: string; message?: string }
  try {
    envelope = raw ? (parseResponse(raw) as typeof envelope) : {}
  } catch {
    envelope = {}
  }
  if (!response.ok) {
    if (response.status === 401 && sentToken && sentToken === token) onExpired()
    throw new ApiError(
      response.status,
      envelope.code ?? `HTTP_${response.status}`,
      envelope.message ?? '요청을 처리하지 못했습니다.',
    )
  }
  if (!Object.hasOwn(envelope, 'data'))
    throw new ApiError(
      0,
      'INVALID_RESPONSE',
      '서버 응답 형식을 확인할 수 없습니다. 결과를 다시 확인해 주세요.',
    )
  return envelope.data as T
}
export function params(values: Record<string, string | number | undefined>) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(values))
    if (value !== '' && value !== undefined) query.set(key, String(value))
  return query.toString()
}
export function errorHelp(error: unknown) {
  if (!(error instanceof ApiError)) return '입력한 내용을 확인해 주세요.'
  if (error.code === 'DUPLICATE_USERNAME')
    return '이미 사용 중인 아이디입니다. 다른 아이디를 입력해 주세요.'
  if (error.code === 'LOGIN_FAILED') return '아이디와 비밀번호를 다시 확인해 주세요.'
  if (error.code === 'ORDER_IDEMPOTENCY_KEY_CONFLICT')
    return '이 요청 번호에 다른 주문 내용이 등록되어 있습니다. 새 주문을 만들기 전에 담당자에게 확인해 주세요.'
  if (error.code === 'ORDER_CREATION_RECOVERY_REQUIRED')
    return '주문 처리 결과 확인이 필요합니다. 새 주문을 만들지 말고 관리자에게 요청 번호를 전달해 주세요.'
  if (error.code === 'ORDER_CREATION_IN_PROGRESS')
    return '이전 주문이 처리 중입니다. 잠시 후 같은 요청의 결과를 확인해 주세요.'
  if (error.code === 'ORDER_CREATION_ABORTED')
    return '이 주문 생성은 종료되었습니다. 원인을 해결한 뒤 새 요청으로 다시 작성할 수 있습니다.'
  if (error.code === 'DELIVERY_MANAGER_NOT_FOUND')
    return '배송 구간을 맡을 담당자가 해당 허브에 없어 배송을 만들지 못했고, 주문도 남기지 않았습니다. 관리자가 사용자 관리에서 그 허브의 배송담당자를 지정한 뒤 다시 주문해 주세요.'
  if (error.status === 401) return '세션이 만료되었습니다. 다시 로그인해 주세요.'
  if (error.status === 403) return '현재 계정에 이 작업의 권한이 없습니다.'
  if (error.status === 404)
    return '대상이 삭제되었거나 존재하지 않습니다. 목록을 새로고침해 주세요.'
  if (error.status === 409)
    return '현재 상태에서는 처리할 수 없습니다. 최신 주문·배송 상태를 확인해 주세요.'
  if (error.status >= 500 || error.status === 0)
    return '처리가 완료됐을 수도 있습니다. 주문 생성은 같은 요청 번호로 결과를 확인하고, 다른 작업은 최신 상태를 먼저 조회해 주세요.'
  return '입력한 내용을 확인해 주세요.'
}
