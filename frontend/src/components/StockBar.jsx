function getStockTone(percentage) {
  if (percentage > 50) {
    return {
      bar: 'from-emerald-400 to-green-500',
      text: 'text-emerald-300',
      label: 'Còn nhiều vé',
    }
  }
  if (percentage > 20) {
    return {
      bar: 'from-amber-300 to-orange-400',
      text: 'text-amber-300',
      label: 'Vé đang hết nhanh',
    }
  }
  return {
    bar: 'from-red-500 to-orange-500',
    text: 'text-red-300',
    label: percentage > 0 ? 'Sắp hết vé' : 'Đã hết vé',
  }
}

export default function StockBar({ remaining = 0, total = 0 }) {
  const safeTotal = Math.max(0, total)
  const safeRemaining = Math.min(Math.max(0, remaining), safeTotal || remaining)
  const percentage = safeTotal > 0 ? (safeRemaining / safeTotal) * 100 : 0
  const tone = getStockTone(percentage)

  return (
    <section aria-label="Tình trạng vé" className="mt-7">
      <div className="mb-2.5 flex items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-zinc-500">
            Vé còn lại
          </p>
          <p className={`mt-1 text-sm font-semibold ${tone.text}`}>{tone.label}</p>
        </div>
        <p className="tabular-nums text-right text-sm text-zinc-400">
          <strong className="text-2xl font-black text-white">{safeRemaining}</strong>
          <span className="mx-1 text-zinc-600">/</span>
          {safeTotal}
        </p>
      </div>

      <div
        className="h-2.5 overflow-hidden rounded-full bg-zinc-800/90 ring-1 ring-inset ring-white/5"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={safeTotal}
        aria-valuenow={safeRemaining}
        aria-label={`Còn ${safeRemaining} trên tổng số ${safeTotal} vé`}
      >
        <div
          className={`h-full rounded-full bg-gradient-to-r ${tone.bar} transition-all duration-700 ease-out`}
          style={{ width: `${Math.min(100, Math.max(0, percentage))}%` }}
        />
      </div>
    </section>
  )
}
