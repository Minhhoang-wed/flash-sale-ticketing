import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, reserveEvent } from './api'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('apiFetch', () => {
  it('gắn user header, parse JSON và ghi request log', async () => {
    const onRequest = vi.fn()
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ reservationId: 'res-1', status: 'PENDING' }), {
        status: 202,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await reserveEvent(1, 'user-abc123', { onRequest })

    expect(result.status).toBe(202)
    expect(result.data.status).toBe('PENDING')
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/events/1/reserve',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ 'X-User-Id': 'user-abc123' }),
      }),
    )
    expect(onRequest).toHaveBeenCalledWith(
      expect.objectContaining({ method: 'POST', path: '/api/events/1/reserve', status: 202 }),
    )
  })

  it('ném ApiError với message JSON của backend', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ message: 'Event 1 is sold out' }), {
        status: 409,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(apiFetch('/api/events/1/reserve', { method: 'POST' })).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
      message: 'Event 1 is sold out',
    })
  })

  it('ghi status 0 cho lỗi mạng nhưng bỏ qua request bị abort', async () => {
    const onRequest = vi.fn()
    vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new TypeError('network down'))

    await expect(apiFetch('/api/events/1', { onRequest })).rejects.toThrow('network down')
    expect(onRequest).toHaveBeenCalledWith(expect.objectContaining({ status: 0 }))

    const abortError = new DOMException('aborted', 'AbortError')
    vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(abortError)
    await expect(apiFetch('/api/events/1', { onRequest })).rejects.toBe(abortError)
    expect(onRequest).toHaveBeenCalledTimes(1)
  })

  it('exposes a dedicated ApiError type', () => {
    const error = new ApiError('conflict', { status: 409 })
    expect(error).toBeInstanceOf(Error)
    expect(error.status).toBe(409)
  })
})
