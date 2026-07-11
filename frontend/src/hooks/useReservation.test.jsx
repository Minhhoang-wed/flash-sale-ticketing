import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../lib/api'
import {
  initialReservationState,
  RESERVATION_STATUS,
  reservationReducer,
  useReservation,
} from './useReservation'

vi.mock('../lib/api', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    reserveEvent: vi.fn(),
    getOrderByReservation: vi.fn(),
    payOrder: vi.fn(),
  }
})

import { getOrderByReservation, payOrder, reserveEvent } from '../lib/api'

const reservedOrder = {
  id: 12,
  userId: 'user-abc123',
  eventId: 1,
  status: 'RESERVED',
  reservationId: 'res-1',
  createdAt: '2026-07-10T12:00:00Z',
  expiresAt: '2026-07-10T12:10:00Z',
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.clearAllMocks()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('reservationReducer', () => {
  it('đi đúng chuỗi RESERVING → POLLING → RESERVED → PAID', () => {
    const reserving = reservationReducer(initialReservationState, { type: 'RESERVE_START' })
    const polling = reservationReducer(reserving, {
      type: 'RESERVE_ACCEPTED',
      reservationId: 'res-1',
    })
    const reserved = reservationReducer(polling, { type: 'POLL_FOUND', order: reservedOrder })
    const paid = reservationReducer(reserved, {
      type: 'PAY_OK',
      order: { ...reservedOrder, status: 'PAID' },
    })

    expect(reserving.status).toBe(RESERVATION_STATUS.RESERVING)
    expect(polling.status).toBe(RESERVATION_STATUS.POLLING)
    expect(reserved.status).toBe(RESERVATION_STATUS.RESERVED)
    expect(paid.status).toBe(RESERVATION_STATUS.PAID)
  })

  it('giữ reservation id khi polling timeout và retry', () => {
    const polling = {
      ...initialReservationState,
      status: RESERVATION_STATUS.POLLING,
      reservationId: 'res-1',
    }
    const queued = reservationReducer(polling, { type: 'POLL_TIMEOUT' })
    const retry = reservationReducer(queued, { type: 'POLL_RETRY' })

    expect(queued.status).toBe(RESERVATION_STATUS.QUEUED)
    expect(retry.status).toBe(RESERVATION_STATUS.POLLING)
    expect(retry.reservationId).toBe('res-1')
    expect(retry.pollRun).toBe(1)
  })
})

describe('useReservation', () => {
  it('reserve 202 rồi poll thành order RESERVED', async () => {
    reserveEvent.mockResolvedValue({
      status: 202,
      data: { reservationId: 'res-1', status: 'PENDING' },
    })
    getOrderByReservation.mockResolvedValue({ data: reservedOrder, status: 200 })

    const { result } = renderHook(() =>
      useReservation({ eventId: 1, userId: 'user-abc123' }),
    )

    await act(async () => result.current.reserve())
    expect(result.current.state.status).toBe(RESERVATION_STATUS.POLLING)

    await act(async () => vi.advanceTimersByTimeAsync(700))
    expect(result.current.state.status).toBe(RESERVATION_STATUS.RESERVED)
    expect(result.current.state.order.id).toBe(12)
  })

  it('phân loại đúng 409 already reserved và 429', async () => {
    reserveEvent.mockRejectedValueOnce(
      new ApiError('User already reserved a ticket', { status: 409 }),
    )
    const onToast = vi.fn()
    const { result } = renderHook(() =>
      useReservation({ eventId: 1, userId: 'user-abc123', onToast }),
    )

    await act(async () => result.current.reserve())
    expect(result.current.state.status).toBe(RESERVATION_STATUS.ALREADY_OWNED)

    reserveEvent.mockRejectedValueOnce(
      new ApiError('Max 5 requests/second', { status: 429 }),
    )
    await act(async () => result.current.reserve())
    expect(result.current.state.status).toBe(RESERVATION_STATUS.RATE_LIMITED)
    expect(onToast).toHaveBeenCalledWith('Chậm thôi! Tối đa 5 lần/giây', 'warning')

    await act(async () => vi.advanceTimersByTimeAsync(2_000))
    expect(result.current.state.status).toBe(RESERVATION_STATUS.IDLE)
  })

  it('thanh toán thành công và chuyển PAID', async () => {
    reserveEvent.mockResolvedValue({
      status: 202,
      data: { reservationId: 'res-1', status: 'PENDING' },
    })
    getOrderByReservation.mockResolvedValue({ data: reservedOrder, status: 200 })
    payOrder.mockResolvedValue({ data: { ...reservedOrder, status: 'PAID' }, status: 200 })

    const { result } = renderHook(() =>
      useReservation({ eventId: 1, userId: 'user-abc123' }),
    )

    await act(async () => result.current.reserve())
    await act(async () => vi.advanceTimersByTimeAsync(700))
    await act(async () => result.current.pay())

    expect(result.current.state.status).toBe(RESERVATION_STATUS.PAID)
    expect(result.current.state.order.status).toBe('PAID')
  })
})
