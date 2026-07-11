import { useCallback, useEffect, useReducer, useRef } from 'react'
import {
  ApiError,
  getOrderByReservation,
  payOrder,
  reserveEvent,
} from '../lib/api'

export const RESERVATION_STATUS = {
  IDLE: 'IDLE',
  RESERVING: 'RESERVING',
  POLLING: 'POLLING',
  QUEUED: 'QUEUED',
  RESERVED: 'RESERVED',
  PAID: 'PAID',
  SOLD_OUT: 'SOLD_OUT',
  ALREADY_OWNED: 'ALREADY_OWNED',
  RATE_LIMITED: 'RATE_LIMITED',
  EXPIRED: 'EXPIRED',
  ERROR: 'ERROR',
}

export const initialReservationState = {
  status: RESERVATION_STATUS.IDLE,
  reservationId: null,
  order: null,
  pollAttempts: 0,
  pollRun: 0,
  paymentPending: false,
  paymentError: null,
  message: null,
}

export function reservationReducer(state, action) {
  switch (action.type) {
    case 'RESET':
      return { ...initialReservationState }
    case 'RESERVE_START':
      return {
        ...initialReservationState,
        status: RESERVATION_STATUS.RESERVING,
      }
    case 'RESERVE_ACCEPTED':
      return {
        ...state,
        status: RESERVATION_STATUS.POLLING,
        reservationId: action.reservationId,
        pollAttempts: 0,
        message: action.sync
          ? 'Đã giữ chỗ, đang lấy thông tin đơn...'
          : 'Yêu cầu đã vào hàng đợi xử lý.',
      }
    case 'POLL_ATTEMPT':
      return {
        ...state,
        pollAttempts: action.attempt,
      }
    case 'POLL_FOUND':
      return {
        ...state,
        status:
          action.order.status === 'PAID'
            ? RESERVATION_STATUS.PAID
            : RESERVATION_STATUS.RESERVED,
        order: action.order,
        reservationId: action.order.reservationId || state.reservationId,
        paymentPending: false,
        paymentError: null,
        message: null,
      }
    case 'POLL_TIMEOUT':
      return {
        ...state,
        status: RESERVATION_STATUS.QUEUED,
        message: 'Đang xếp hàng xử lý lâu hơn dự kiến.',
      }
    case 'POLL_RETRY':
      return {
        ...state,
        status: RESERVATION_STATUS.POLLING,
        pollAttempts: 0,
        pollRun: state.pollRun + 1,
        message: 'Đang kiểm tra lại hàng đợi...',
      }
    case 'SOLD_OUT':
      return {
        ...initialReservationState,
        status: RESERVATION_STATUS.SOLD_OUT,
        message: action.message,
      }
    case 'ALREADY_OWNED':
      return {
        ...initialReservationState,
        status: RESERVATION_STATUS.ALREADY_OWNED,
        message: action.message,
      }
    case 'RATE_LIMITED':
      return {
        ...initialReservationState,
        status: RESERVATION_STATUS.RATE_LIMITED,
        message: action.message,
      }
    case 'PAY_START':
      return {
        ...state,
        paymentPending: true,
        paymentError: null,
      }
    case 'PAY_OK':
      return {
        ...state,
        status: RESERVATION_STATUS.PAID,
        order: action.order,
        paymentPending: false,
        paymentError: null,
      }
    case 'PAY_ERROR':
      return {
        ...state,
        paymentPending: false,
        paymentError: action.message,
      }
    case 'EXPIRE':
      return {
        ...state,
        status: RESERVATION_STATUS.EXPIRED,
        paymentPending: false,
        message: action.message || 'Thời gian giữ chỗ đã kết thúc.',
      }
    case 'ERROR':
      return {
        ...state,
        status: RESERVATION_STATUS.ERROR,
        paymentPending: false,
        message: action.message,
      }
    default:
      return state
  }
}

function classifyOrder(order, dispatch) {
  if (order.status === 'RESERVED' || order.status === 'PAID') {
    dispatch({ type: 'POLL_FOUND', order })
    return
  }

  if (order.status === 'EXPIRED' || order.status === 'CANCELLED') {
    dispatch({
      type: 'EXPIRE',
      message:
        order.status === 'CANCELLED'
          ? 'Đơn giữ chỗ đã bị huỷ.'
          : 'Đơn giữ chỗ đã hết hạn.',
    })
    return
  }

  dispatch({ type: 'ERROR', message: `Trạng thái đơn không hỗ trợ: ${order.status}` })
}

export function useReservation({ eventId = 1, userId, onRequest, onToast } = {}) {
  const [state, dispatch] = useReducer(reservationReducer, initialReservationState)
  const operationControllerRef = useRef(null)

  const cancelOperation = useCallback(() => {
    operationControllerRef.current?.abort()
    operationControllerRef.current = null
  }, [])

  const reset = useCallback(() => {
    cancelOperation()
    dispatch({ type: 'RESET' })
  }, [cancelOperation])

  const reserve = useCallback(async () => {
    cancelOperation()
    const controller = new AbortController()
    operationControllerRef.current = controller
    dispatch({ type: 'RESERVE_START' })

    try {
      const { data, status } = await reserveEvent(eventId, userId, {
        signal: controller.signal,
        onRequest,
      })

      if (controller.signal.aborted) return
      dispatch({
        type: 'RESERVE_ACCEPTED',
        reservationId: data.reservationId,
        sync: status === 201,
      })
    } catch (error) {
      if (error.name === 'AbortError') return

      const message = error.message || 'Không thể gửi yêu cầu giữ vé.'
      const normalizedMessage = message.toLowerCase()

      if (error instanceof ApiError && error.status === 429) {
        dispatch({ type: 'RATE_LIMITED', message })
        onToast?.('Chậm thôi! Tối đa 5 lần/giây', 'warning')
      } else if (
        error instanceof ApiError &&
        error.status === 409 &&
        normalizedMessage.includes('already reserved')
      ) {
        dispatch({ type: 'ALREADY_OWNED', message })
      } else if (
        error instanceof ApiError &&
        error.status === 409 &&
        normalizedMessage.includes('sold out')
      ) {
        dispatch({ type: 'SOLD_OUT', message })
      } else {
        dispatch({ type: 'ERROR', message })
      }
    } finally {
      if (operationControllerRef.current === controller) {
        operationControllerRef.current = null
      }
    }
  }, [cancelOperation, eventId, onRequest, onToast, userId])

  const pay = useCallback(async () => {
    if (!state.order?.id || state.paymentPending) return

    cancelOperation()
    const controller = new AbortController()
    operationControllerRef.current = controller
    dispatch({ type: 'PAY_START' })

    try {
      const { data } = await payOrder(state.order.id, {
        signal: controller.signal,
        onRequest,
      })

      if (controller.signal.aborted) return
      dispatch({ type: 'PAY_OK', order: data })
      onToast?.('Thanh toán thành công — vé đã thuộc về bạn!', 'success')
    } catch (error) {
      if (error.name === 'AbortError') return

      const message = error.message || 'Thanh toán chưa thành công.'
      if (
        error instanceof ApiError &&
        error.status === 409 &&
        message.toLowerCase().includes('expired')
      ) {
        dispatch({ type: 'EXPIRE', message })
      } else {
        dispatch({ type: 'PAY_ERROR', message })
      }
    } finally {
      if (operationControllerRef.current === controller) {
        operationControllerRef.current = null
      }
    }
  }, [cancelOperation, onRequest, onToast, state.order, state.paymentPending])

  const retryPolling = useCallback(() => {
    dispatch({ type: 'POLL_RETRY' })
  }, [])

  const expire = useCallback(() => {
    cancelOperation()
    dispatch({ type: 'EXPIRE' })
  }, [cancelOperation])

  useEffect(() => {
    if (state.status !== RESERVATION_STATUS.POLLING || !state.reservationId) {
      return undefined
    }

    let active = true
    let timerId
    const controller = new AbortController()

    const wait = (delay) =>
      new Promise((resolve) => {
        timerId = window.setTimeout(resolve, delay)
      })

    const poll = async () => {
      for (let attempt = 1; attempt <= 15; attempt += 1) {
        await wait(700)
        if (!active) return

        dispatch({ type: 'POLL_ATTEMPT', attempt })

        try {
          const { data } = await getOrderByReservation(state.reservationId, {
            signal: controller.signal,
            onRequest,
          })

          if (!active) return
          classifyOrder(data, dispatch)
          return
        } catch (error) {
          if (!active || error.name === 'AbortError') return
          if (error instanceof ApiError && error.status === 404) continue

          dispatch({
            type: 'ERROR',
            message: error.message || 'Không thể kiểm tra trạng thái giữ vé.',
          })
          return
        }
      }

      if (active) dispatch({ type: 'POLL_TIMEOUT' })
    }

    poll()

    return () => {
      active = false
      controller.abort()
      window.clearTimeout(timerId)
    }
  }, [onRequest, state.pollRun, state.reservationId, state.status])

  useEffect(() => {
    if (state.status !== RESERVATION_STATUS.RATE_LIMITED) return undefined
    const timeoutId = window.setTimeout(() => dispatch({ type: 'RESET' }), 2_000)
    return () => window.clearTimeout(timeoutId)
  }, [state.status])

  useEffect(() => {
    cancelOperation()
    dispatch({ type: 'RESET' })
  }, [cancelOperation, userId])

  useEffect(() => cancelOperation, [cancelOperation])

  return {
    state,
    reserve,
    pay,
    retryPolling,
    expire,
    reset,
  }
}
