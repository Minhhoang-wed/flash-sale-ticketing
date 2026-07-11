import { useEffect, useMemo, useRef, useState } from 'react'

function remainingUntil(expiresAt) {
  const expires = new Date(expiresAt).getTime()
  if (!Number.isFinite(expires)) return 0
  return Math.max(0, expires - Date.now())
}

function formatCountdown(milliseconds) {
  const totalSeconds = Math.max(0, Math.ceil(milliseconds / 1_000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function shortenCode(value) {
  if (!value) return '—'
  return value.length > 12 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value
}

export default function ReservationPanel({ order, paymentPending, paymentError, onPay, onExpire }) {
  const [remaining, setRemaining] = useState(() => remainingUntil(order.expiresAt))
  const expiredNotifiedRef = useRef(false)

  const totalDuration = useMemo(() => {
    const start = new Date(order.createdAt).getTime()
    const end = new Date(order.expiresAt).getTime()
    return Number.isFinite(start) && Number.isFinite(end) ? Math.max(1, end - start) : 1
  }, [order.createdAt, order.expiresAt])

  useEffect(() => {
    expiredNotifiedRef.current = false

    const update = () => {
      const next = remainingUntil(order.expiresAt)
      setRemaining(next)
      if (next === 0 && !expiredNotifiedRef.current) {
        expiredNotifiedRef.current = true
        onExpire()
      }
    }

    update()
    const intervalId = window.setInterval(update, 250)
    return () => window.clearInterval(intervalId)
  }, [onExpire, order.expiresAt])

  const ratio = Math.min(1, Math.max(0, remaining / totalDuration))
  const urgent = remaining < 60_000
  const ringColor = urgent ? '#ef4444' : '#f97316'

  return (
    <section className="mt-7 animate-fade-up" aria-live="polite">
      <div className="flex items-center gap-3 rounded-2xl border border-emerald-400/20 bg-emerald-400/[0.07] p-4">
        <span
          className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-emerald-400/15 text-xl"
          aria-hidden="true"
        >
          ✓
        </span>
        <div>
          <p className="font-bold text-emerald-300">Giữ chỗ thành công!</p>
          <p className="mt-0.5 text-xs text-zinc-400">
            Mã giữ chỗ:{' '}
            <span className="font-mono text-zinc-200" title={order.reservationId}>
              {shortenCode(order.reservationId)}
            </span>
          </p>
        </div>
      </div>

      <div className="mt-6 flex flex-col items-center">
        <div
          className="grid h-40 w-40 place-items-center rounded-full p-2 transition-all duration-300"
          style={{
            background: `conic-gradient(${ringColor} ${ratio * 360}deg, rgba(63, 63, 70, .65) 0deg)`,
          }}
          role="timer"
          aria-label={`Còn ${formatCountdown(remaining)} để thanh toán`}
        >
          <div className="grid h-full w-full place-items-center rounded-full bg-zinc-950 text-center shadow-inner">
            <div>
              <p className="text-[10px] font-bold uppercase tracking-[0.2em] text-zinc-500">
                Giữ chỗ còn
              </p>
              <p
                className={`mt-1 tabular-nums text-4xl font-black tracking-tight ${
                  urgent ? 'text-red-400' : 'text-white'
                }`}
              >
                {formatCountdown(remaining)}
              </p>
            </div>
          </div>
        </div>
        <p className={`mt-3 text-center text-xs ${urgent ? 'text-red-300' : 'text-zinc-500'}`}>
          {urgent ? 'Sắp hết thời gian — thanh toán ngay!' : 'Vé sẽ tự động trả về kho khi hết giờ.'}
        </p>
      </div>

      {paymentError && (
        <p className="mt-4 rounded-xl border border-red-400/20 bg-red-500/10 px-3 py-2.5 text-center text-sm text-red-200">
          {paymentError}
        </p>
      )}

      <button
        type="button"
        onClick={onPay}
        disabled={paymentPending || remaining <= 0}
        className="mt-5 flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-red-500 via-orange-500 to-amber-400 px-5 py-4 text-base font-black text-white shadow-glow-red transition hover:-translate-y-0.5 hover:brightness-110 focus:outline-none focus-visible:ring-2 focus-visible:ring-orange-300 focus-visible:ring-offset-2 focus-visible:ring-offset-zinc-950 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0"
      >
        {paymentPending ? (
          <>
            <span className="h-5 w-5 animate-spin rounded-full border-2 border-white/30 border-t-white" />
            Đang thanh toán...
          </>
        ) : (
          <>Thanh toán ngay <span aria-hidden="true">→</span></>
        )}
      </button>
    </section>
  )
}
