import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from './api'

afterEach(() => vi.unstubAllGlobals())

describe('api 통신 오류 변환', () => {
  it('응답 헤더 뒤 본문 수신이 끊기면 결과 미확정 ApiError(0)로 바꾼다', async () => {
    const body = new ReadableStream({
      start(controller) {
        controller.error(new TypeError('network error'))
      },
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status: 201 })))

    const error = await api('/companies', { method: 'POST', body: {} }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })

  it('사용자가 취소한 요청은 원래 오류를 그대로 던진다', async () => {
    const controller = new AbortController()
    const abort = new DOMException('aborted', 'AbortError')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => {
        controller.abort()
        return Promise.reject(abort)
      }),
    )

    await expect(api('/hubs', { signal: controller.signal })).rejects.toBe(abort)
  })
})
