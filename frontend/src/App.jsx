import { useCallback, useEffect, useMemo, useState } from 'react'
import DebugPanel from './components/DebugPanel'
import EventCard from './components/EventCard'
import Toast from './components/Toast'
import { useEvent } from './hooks/useEvent'
import { RESERVATION_STATUS, useReservation } from './hooks/useReservation'
import { createUserId, getOrCreateUserId, saveUserId } from './lib/user'

function LoadingCard() {
  return (
    <div className="rounded-[1.75rem] border border-white/10 bg-zinc-900/70 p-7 shadow-glow" role="status">
      <div className="h-7 w-28 animate-pulse rounded-full bg-zinc-800" />
      <div className="mt-7 h-9 w-4/5 animate-pulse rounded-lg bg-zinc-800" />
      <div className="mt-3 h-4 w-full animate-pulse rounded bg-zinc-800/70" />
      <div className="mt-2 h-4 w-2/3 animate-pulse rounded bg-zinc-800/70" />
      <div className="mt-8 h-2.5 animate-pulse rounded-full bg-zinc-800" />
      <div className="mt-7 h-14 animate-pulse rounded-2xl bg-zinc-800" />
      <span className="sr-only">Đang tải sự kiện...</span>
    </div>
  )
}

function EventLoadError({ message, onRetry }) {
  return (
    <div className="rounded-[1.75rem] border border-red-400/20 bg-zinc-900/80 p-7 text-center shadow-glow">
      <span className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-red-500/10 text-xl text-red-300">
        !
      </span>
      <h1 className="mt-4 text-xl font-black text-white">Chưa kết nối được với sự kiện</h1>
      <p className="mt-2 text-sm leading-6 text-zinc-400">
        {message || 'Hãy chắc chắn backend đang chạy, rồi thử lại.'}
      </p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-5 rounded-xl bg-white px-5 py-3 text-sm font-black text-zinc-950 transition hover:bg-orange-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-orange-300"
      >
        Kết nối lại
      </button>
    </div>
  )
}

export default function App() {
  const [userId, setUserId] = useState(getOrCreateUserId)
  const [logs, setLogs] = useState([])
  const [toast, setToast] = useState(null)

  const logRequest = useCallback((entry) => {
    setLogs((current) => [
      {
        ...entry,
        id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      },
      ...current,
    ].slice(0, 20))
  }, [])

  const showToast = useCallback((message, type = 'info', duration = 3_000) => {
    setToast({ id: Date.now(), message, type, duration })
  }, [])

  const reservation = useReservation({
    eventId: 1,
    userId,
    onRequest: logRequest,
    onToast: showToast,
  })

  const pauseEventRefresh = useMemo(
    () =>
      reservation.state.status === RESERVATION_STATUS.RESERVED ||
      reservation.state.status === RESERVATION_STATUS.PAID,
    [reservation.state.status],
  )

  const { event, loading, error, refresh } = useEvent({
    eventId: 1,
    paused: pauseEventRefresh,
    onRequest: logRequest,
  })

  const changeUser = useCallback(() => {
    reservation.reset()
    const nextUserId = createUserId()
    saveUserId(nextUserId)
    setUserId(nextUserId)
    showToast(`Đã chuyển sang ${nextUserId}`, 'info', 2_200)
  }, [reservation, showToast])

  const resetFlow = useCallback(() => {
    reservation.reset()
  }, [reservation])

  const afterDemoReset = useCallback(async () => {
    reservation.reset()
    await refresh()
    showToast('Đã reset kho vé và toàn bộ đơn demo.', 'success')
  }, [refresh, reservation, showToast])

  useEffect(() => {
    if (
      reservation.state.status === RESERVATION_STATUS.POLLING ||
      reservation.state.status === RESERVATION_STATUS.SOLD_OUT ||
      reservation.state.status === RESERVATION_STATUS.EXPIRED
    ) {
      refresh()
    }
  }, [refresh, reservation.state.status])

  return (
    <div className="relative min-h-screen overflow-x-clip bg-zinc-950 text-zinc-100 selection:bg-orange-400/30">
      <div className="page-grid pointer-events-none absolute inset-0 opacity-40" />
      <div className="pointer-events-none absolute left-1/2 top-[-13rem] h-[32rem] w-[32rem] -translate-x-1/2 rounded-full bg-red-600/10 blur-[110px]" />
      <div className="pointer-events-none absolute bottom-[-16rem] right-[-10rem] h-[30rem] w-[30rem] rounded-full bg-orange-500/[0.07] blur-[120px]" />

      <header className="relative z-10 mx-auto flex w-full max-w-6xl items-center justify-between gap-3 px-4 py-5 sm:px-6">
        <a
          href="/"
          className="flex items-center gap-2.5 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-orange-400"
          aria-label="FlashTix — trang chủ"
        >
          <span className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-red-500 to-orange-400 text-lg shadow-lg shadow-red-950/40" aria-hidden="true">
            ⚡
          </span>
          <span>
            <strong className="block text-sm font-black tracking-tight text-white">FlashTix</strong>
            <span className="hidden text-[10px] font-semibold uppercase tracking-[0.15em] text-zinc-600 sm:block">
              Ticketing live
            </span>
          </span>
        </a>

        <div className="flex min-w-0 items-center gap-2 rounded-full border border-white/[0.08] bg-white/[0.04] py-1.5 pl-3 pr-1.5 backdrop-blur">
          <span className="h-2 w-2 shrink-0 rounded-full bg-emerald-400 shadow-[0_0_10px_rgba(52,211,153,.7)]" />
          <span className="max-w-[100px] truncate font-mono text-[11px] font-semibold text-zinc-300 sm:max-w-none">
            {userId}
          </span>
          <button
            type="button"
            onClick={changeUser}
            className="rounded-full bg-white/[0.07] px-2.5 py-1.5 text-[10px] font-bold text-zinc-300 transition hover:bg-white/[0.12] hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-orange-400"
          >
            Đổi user
          </button>
        </div>
      </header>

      <main className="relative z-10 mx-auto flex w-full max-w-[468px] flex-1 flex-col justify-center px-4 pb-28 pt-7 sm:px-6 sm:pb-24 sm:pt-12">
        <div className="mb-5 flex items-center justify-center gap-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-zinc-600">
          <span className="h-px w-7 bg-gradient-to-r from-transparent to-zinc-700" />
          Live inventory
          <span className="h-px w-7 bg-gradient-to-l from-transparent to-zinc-700" />
        </div>

        {loading && !event ? (
          <LoadingCard />
        ) : event ? (
          <EventCard
            event={event}
            reservation={reservation.state}
            onReserve={reservation.reserve}
            onPay={reservation.pay}
            onRetryPoll={reservation.retryPolling}
            onExpire={reservation.expire}
            onReset={resetFlow}
          />
        ) : (
          <EventLoadError message={error?.message} onRetry={refresh} />
        )}

        {event && error && !pauseEventRefresh && (
          <p className="mt-3 text-center text-[11px] text-amber-400/80">
            Mất kết nối tạm thời — đang giữ dữ liệu gần nhất và sẽ tự thử lại.
          </p>
        )}
      </main>

      <p className="absolute bottom-5 left-1/2 z-0 hidden -translate-x-1/2 whitespace-nowrap text-[10px] font-semibold uppercase tracking-[0.2em] text-zinc-800 sm:block">
        Fair queue · Atomic stock · One ticket per user
      </p>

      <DebugPanel
        userId={userId}
        logs={logs}
        onChangeUser={changeUser}
        onAfterReset={afterDemoReset}
        onRefreshEvent={refresh}
        onRequest={logRequest}
      />
      <Toast toast={toast} onClose={() => setToast(null)} />
    </div>
  )
}
