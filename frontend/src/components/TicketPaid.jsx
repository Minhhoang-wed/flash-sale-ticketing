const CONFETTI = [
  ['8%', '-10deg', '#fb7185', '0s'],
  ['17%', '14deg', '#fbbf24', '.35s'],
  ['29%', '-22deg', '#34d399', '.12s'],
  ['44%', '8deg', '#fb923c', '.55s'],
  ['58%', '-15deg', '#60a5fa', '.18s'],
  ['72%', '18deg', '#f472b6', '.42s'],
  ['84%', '-8deg', '#a78bfa', '.08s'],
  ['94%', '24deg', '#facc15', '.3s'],
]

function shortenCode(value) {
  if (!value) return '—'
  return value.length > 16 ? `${value.slice(0, 12)}…` : value
}

export default function TicketPaid({ event, order }) {
  return (
    <section className="relative animate-fade-up overflow-hidden rounded-[1.75rem] border border-emerald-300/20 bg-gradient-to-b from-zinc-900 to-zinc-950 shadow-2xl shadow-emerald-950/40">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-32 overflow-hidden" aria-hidden="true">
        {CONFETTI.map(([left, rotate, color, delay]) => (
          <span
            key={`${left}-${color}`}
            className="confetti-piece"
            style={{ left, '--rotate': rotate, '--color': color, animationDelay: delay }}
          />
        ))}
      </div>

      <div className="relative bg-gradient-to-br from-emerald-400 via-green-400 to-teal-500 px-6 pb-8 pt-9 text-zinc-950">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-black uppercase tracking-[0.24em] opacity-70">FlashTix</p>
            <h2 className="mt-4 max-w-[260px] text-2xl font-black leading-tight">{event.name}</h2>
          </div>
          <span className="rounded-full bg-zinc-950 px-3 py-1.5 text-xs font-black tracking-wider text-emerald-300">
            PAID
          </span>
        </div>
        <div className="mt-8 flex items-center gap-2 text-sm font-bold">
          <span className="grid h-8 w-8 place-items-center rounded-full bg-white/40" aria-hidden="true">
            ✓
          </span>
          Thanh toán thành công
        </div>
      </div>

      <div className="ticket-cut-line relative border-t border-dashed border-zinc-700 px-6 py-6">
        <div className="grid grid-cols-2 gap-5">
          <div>
            <p className="ticket-label">Chủ vé</p>
            <p className="mt-1 truncate font-mono text-sm font-semibold text-zinc-100" title={order.userId}>
              {order.userId}
            </p>
          </div>
          <div>
            <p className="ticket-label">Mã đơn</p>
            <p className="mt-1 font-mono text-sm font-semibold text-zinc-100" title={String(order.id)}>
              #{String(order.id).padStart(5, '0')}
            </p>
          </div>
          <div className="col-span-2">
            <p className="ticket-label">Mã giữ chỗ</p>
            <p className="mt-1 font-mono text-sm text-zinc-300" title={order.reservationId}>
              {shortenCode(order.reservationId)}
            </p>
          </div>
        </div>

        <div className="mt-6 flex items-center justify-between rounded-xl bg-white/[0.04] px-4 py-3 text-xs text-zinc-500">
          <span>Vé điện tử hợp lệ</span>
          <span className="font-semibold text-emerald-400">● Đã xác nhận</span>
        </div>
      </div>
    </section>
  )
}
