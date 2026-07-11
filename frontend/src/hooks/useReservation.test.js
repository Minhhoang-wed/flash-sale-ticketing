import { describe, expect, it } from 'vitest'
import {
  initialReservationState,
  reservationReducer,
  RESERVATION_STATUS,
} from './useReservation'

function transition(actions, seed = initialReservationState) {
  return actions.reduce(reservationReducer, seed)
}

describe('reservationReducer', () => {
  it('starts a clean reservation and moves an accepted request into polling', () => {
    const staleState = {
      ...initialReservationState,
      status: RESERVATION_STATUS.ERROR,
      order: { id: 8 },
      reservationId: 'old-reservation',
      paymentError: 'old payment error',
      message: 'old error',
    }

    const state = transition(
      [
        { type: 'RESERVE_START' },
        { type: 'RESERVE_ACCEPTED', reservationId: 'res-100', sync: false },
      ],
      staleState,
    )

    expect(state).toEqual({
      ...initialReservationState,
      status: RESERVATION_STATUS.POLLING,
      reservationId: 'res-100',
      message: 'Yêu cầu đã vào hàng đợi xử lý.',
    })
  })

  it('tracks polling attempts, timeout, and a new polling run', () => {
    const state = transition([
      { type: 'RESERVE_START' },
      { type: 'RESERVE_ACCEPTED', reservationId: 'res-1', sync: true },
      { type: 'POLL_ATTEMPT', attempt: 15 },
      { type: 'POLL_TIMEOUT' },
      { type: 'POLL_RETRY' },
    ])

    expect(state).toMatchObject({
      status: RESERVATION_STATUS.POLLING,
      reservationId: 'res-1',
      pollAttempts: 0,
      pollRun: 1,
      message: 'Đang kiểm tra lại hàng đợi...',
    })
  })

  it.each([
    ['RESERVED', RESERVATION_STATUS.RESERVED],
    ['PAID', RESERVATION_STATUS.PAID],
  ])('classifies a found %s order', (orderStatus, expectedStatus) => {
    const order = {
      id: 12,
      status: orderStatus,
      reservationId: 'canonical-reservation-id',
    }
    const state = reservationReducer(
      {
        ...initialReservationState,
        status: RESERVATION_STATUS.POLLING,
        reservationId: 'temporary-reservation-id',
        paymentPending: true,
        paymentError: 'stale error',
        message: 'polling',
      },
      { type: 'POLL_FOUND', order },
    )

    expect(state).toMatchObject({
      status: expectedStatus,
      order,
      reservationId: 'canonical-reservation-id',
      paymentPending: false,
      paymentError: null,
      message: null,
    })
  })

  it('keeps the reservation while payment fails, then completes payment', () => {
    const reservedOrder = {
      id: 25,
      status: 'RESERVED',
      reservationId: 'res-25',
    }
    const paidOrder = { ...reservedOrder, status: 'PAID' }
    const reservedState = {
      ...initialReservationState,
      status: RESERVATION_STATUS.RESERVED,
      reservationId: reservedOrder.reservationId,
      order: reservedOrder,
    }

    const failedState = transition(
      [
        { type: 'PAY_START' },
        { type: 'PAY_ERROR', message: 'Payment gateway unavailable' },
      ],
      reservedState,
    )
    expect(failedState).toMatchObject({
      status: RESERVATION_STATUS.RESERVED,
      order: reservedOrder,
      paymentPending: false,
      paymentError: 'Payment gateway unavailable',
    })

    const paidState = transition(
      [{ type: 'PAY_START' }, { type: 'PAY_OK', order: paidOrder }],
      failedState,
    )
    expect(paidState).toMatchObject({
      status: RESERVATION_STATUS.PAID,
      order: paidOrder,
      paymentPending: false,
      paymentError: null,
    })
  })

  it.each([
    ['SOLD_OUT', RESERVATION_STATUS.SOLD_OUT],
    ['ALREADY_OWNED', RESERVATION_STATUS.ALREADY_OWNED],
    ['RATE_LIMITED', RESERVATION_STATUS.RATE_LIMITED],
  ])('clears stale reservation data for %s', (actionType, expectedStatus) => {
    const state = reservationReducer(
      {
        ...initialReservationState,
        status: RESERVATION_STATUS.RESERVED,
        reservationId: 'stale-reservation',
        order: { id: 99 },
        paymentError: 'stale payment error',
      },
      { type: actionType, message: 'terminal message' },
    )

    expect(state).toEqual({
      ...initialReservationState,
      status: expectedStatus,
      message: 'terminal message',
    })
  })

  it('expires a held order and reset returns the exact initial state', () => {
    const expired = reservationReducer(
      {
        ...initialReservationState,
        status: RESERVATION_STATUS.RESERVED,
        reservationId: 'res-expired',
        order: { id: 4, status: 'RESERVED' },
        paymentPending: true,
      },
      { type: 'EXPIRE' },
    )

    expect(expired).toMatchObject({
      status: RESERVATION_STATUS.EXPIRED,
      reservationId: 'res-expired',
      order: { id: 4, status: 'RESERVED' },
      paymentPending: false,
      message: 'Thời gian giữ chỗ đã kết thúc.',
    })
    expect(reservationReducer(expired, { type: 'RESET' })).toEqual(
      initialReservationState,
    )
  })

  it('returns the same object for an unknown action', () => {
    expect(reservationReducer(initialReservationState, { type: 'UNKNOWN' })).toBe(
      initialReservationState,
    )
  })
})
