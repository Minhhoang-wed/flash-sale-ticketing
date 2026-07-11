import { useCallback, useEffect, useRef, useState } from 'react'
import { getEvent } from '../lib/api'

const REFRESH_INTERVAL_MS = 3_000

export function useEvent({ eventId = 1, paused = false, onRequest } = {}) {
  const [event, setEvent] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const controllerRef = useRef(null)
  const hasEventRef = useRef(false)
  const mountedRef = useRef(true)

  const refresh = useCallback(async () => {
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller

    if (!hasEventRef.current && mountedRef.current) setLoading(true)

    try {
      const { data } = await getEvent(eventId, {
        signal: controller.signal,
        onRequest,
      })

      if (controller.signal.aborted || !mountedRef.current) return null
      hasEventRef.current = true
      setEvent(data)
      setError(null)
      return data
    } catch (requestError) {
      if (requestError.name === 'AbortError' || !mountedRef.current) return null
      setError(requestError)
      return null
    } finally {
      if (controllerRef.current === controller) {
        controllerRef.current = null
        if (mountedRef.current) setLoading(false)
      }
    }
  }, [eventId, onRequest])

  useEffect(() => {
    if (paused) {
      controllerRef.current?.abort()
      return undefined
    }

    refresh()
    const intervalId = window.setInterval(refresh, REFRESH_INTERVAL_MS)

    return () => window.clearInterval(intervalId)
  }, [paused, refresh])

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      controllerRef.current?.abort()
    }
  }, [])

  return {
    event,
    loading,
    error,
    refresh,
  }
}
