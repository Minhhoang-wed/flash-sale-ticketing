import { useEffect, useMemo, useState } from 'react'

function getRemaining(targetDate) {
  const target = new Date(targetDate).getTime()
  if (!Number.isFinite(target)) return 0
  return Math.max(0, target - Date.now())
}

function splitDuration(milliseconds) {
  const totalSeconds = Math.ceil(milliseconds / 1_000)
  return {
    days: Math.floor(totalSeconds / 86_400),
    hours: Math.floor((totalSeconds % 86_400) / 3_600),
    minutes: Math.floor((totalSeconds % 3_600) / 60),
    seconds: totalSeconds % 60,
  }
}

export default function CountdownToSale({ targetDate, onComplete }) {
  const [remaining, setRemaining] = useState(() => getRemaining(targetDate))

  useEffect(() => {
    setRemaining(getRemaining(targetDate))

    const update = () => {
      const nextRemaining = getRemaining(targetDate)
      setRemaining(nextRemaining)
      if (nextRemaining === 0) onComplete?.()
    }

    update()
    const intervalId = window.setInterval(update, 1_000)
    return () => window.clearInterval(intervalId)
  }, [onComplete, targetDate])

  const parts = useMemo(() => splitDuration(remaining), [remaining])
  if (remaining <= 0) return null

  return (
    <section className="mt-6 rounded-2xl border border-orange-400/15 bg-orange-500/[0.06] p-4">
      <p className="text-center text-xs font-bold uppercase tracking-[0.2em] text-orange-300">
        Mở bán sau
      </p>
      <div className="mt-3 grid grid-cols-4 gap-2" aria-label="Đếm ngược tới giờ mở bán">
        {[
          ['Ngày', parts.days],
          ['Giờ', parts.hours],
          ['Phút', parts.minutes],
          ['Giây', parts.seconds],
        ].map(([label, value]) => (
          <div key={label} className="rounded-xl bg-zinc-950/70 px-1 py-3 text-center">
            <p className="tabular-nums text-xl font-black text-white">
              {String(value).padStart(2, '0')}
            </p>
            <p className="mt-1 text-[10px] font-semibold uppercase tracking-wider text-zinc-500">
              {label}
            </p>
          </div>
        ))}
      </div>
    </section>
  )
}
