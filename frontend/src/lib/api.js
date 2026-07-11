export class ApiError extends Error {
  constructor(message, details = {}) {
    super(message)
    this.name = 'ApiError'
    Object.assign(this, details)
  }
}

function parseResponseBody(raw, contentType) {
  if (!raw) return null
  if (contentType.includes('application/json')) {
    try {
      return JSON.parse(raw)
    } catch {
      return { message: raw }
    }
  }

  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}

function getErrorMessage(data, status) {
  if (data && typeof data === 'object' && data.message) return data.message
  if (typeof data === 'string' && data.trim()) return data
  return `Yêu cầu thất bại (HTTP ${status})`
}

export async function apiFetch(
  path,
  { method = 'GET', userId, body, signal, onRequest, headers = {} } = {},
) {
  const startedAt = performance.now()
  const requestHeaders = {
    Accept: 'application/json',
    ...headers,
  }

  if (userId) requestHeaders['X-User-Id'] = userId
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json'

  let response
  try {
    response = await fetch(path, {
      method,
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (error) {
    const duration = Math.round(performance.now() - startedAt)
    if (error.name !== 'AbortError') {
      onRequest?.({ method, path, status: 0, duration })
    }
    throw error
  }

  const duration = Math.round(performance.now() - startedAt)
  const raw = await response.text()
  const data = parseResponseBody(raw, response.headers.get('content-type') || '')

  onRequest?.({ method, path, status: response.status, duration })

  if (!response.ok) {
    throw new ApiError(getErrorMessage(data, response.status), {
      status: response.status,
      data,
      duration,
      path,
      method,
    })
  }

  return { data, status: response.status, duration }
}

export function getEvent(eventId = 1, options = {}) {
  return apiFetch(`/api/events/${eventId}`, options)
}

export function reserveEvent(eventId, userId, options = {}) {
  return apiFetch(`/api/events/${eventId}/reserve`, {
    ...options,
    method: 'POST',
    userId,
  })
}

export function getOrderByReservation(reservationId, options = {}) {
  return apiFetch(`/api/orders/by-reservation/${encodeURIComponent(reservationId)}`, options)
}

export function payOrder(orderId, options = {}) {
  return apiFetch(`/api/orders/${orderId}/pay`, {
    ...options,
    method: 'POST',
  })
}

export function resetDemo(options = {}) {
  return apiFetch('/api/debug/reset', {
    ...options,
    method: 'POST',
  })
}
