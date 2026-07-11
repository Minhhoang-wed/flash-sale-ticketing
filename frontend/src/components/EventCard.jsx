import { useCallback, useEffect, useState } from 'react'
import { RESERVATION_STATUS } from '../hooks/useReservation'
import CountdownToSale from './CountdownToSale'
import ReservationPanel from './ReservationPanel'
import StockBar from './StockBar'
import TicketPaid from './TicketPaid'

function isSaleOpen(startSaleAt) {
  const startsAt = new Date(startSaleAt).getTime()
  return !Number.isFinite(startsAt) || startsAt <= Date.now()
}

function Spinner({ className = '' }) {
  return (
    <span
      className={`inline-block h-5 w-5 animate-spin rounded-full border-2 border-white/25 border-t-white ${className}`}
      aria-hidden="true"
    />
  )
}

function MainButton({ children, onClick, disabled = false, pulse = false }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`mt-6 flex w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-red-600 via-orange-500 to-amber-400 px-5 py-4 text-base font-black text-white shadow-glow-red transition duration-200 hover:-translate-y-0.5 hover:brightness-110 focus:outline-none focus-visible:ring-2 focus-visible:ring-orange-300 focus-visible:ring-offset-2 focus-visible:ring-offset-zinc-950 disabled:cursor-not-allowed disabled:from-zinc-700 disabled:via-zinc-700 disabled:to-zinc-600 disabled:opacity-70 disabled:shadow-none disabled:hover:translate-y-0 ${
        pulse && !disabled ? 'animate-cta-pulse' : ''
      }`}
    >
      {children}
    </button>
  )
}

function StatusMessage({ icon, title, children, tone = 'zinc' }) {
  const tones = {
    zinc: 'border-white/10 bg-white/[0.035]',
    red: 'border-red-400/20 bg-red-500/[0.07]',
    amber: 'border-amber-400/20 bg-amber-500/[0.07]',
    violet: 'border-violet-400/20 bg-violet-500/[0.07]',
  }

  return (
    <div className={`mt-7 animate-fade-up rounded-2xl border p-5 text-center ${tones[tone]}`}>
      <span className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-white/[0.06] text-2xl" aria-hidden="true">
        {icon}
      </span>
      <h3 className="mt-3 text-lg font-black text-white">{title}</h3>
      <div className="mt-1.5 text-sm leading-relaxed text-zinc-400">{children}</div>
    </div>
  )
}

export default function EventCard({ event, reservation, onReserve, onPay, onRetryPoll, onExpire, onReset }) {
  const [saleOpen, setSaleOpen] = useState(() => isSaleOpen(event.startSaleAt))

  useEffect(() => {
    setSaleOpen(isSaleOpen(event.startSaleAt))
  }, [event.startSaleAt])

  const handleSaleOpen = useCallback(() => setSaleOpen(true), [])
  const status = reservation.status

  if (status === RESERVATION_STATUS.PAID && reservation.order) {
    return <TicketPaid event={event} order={reservation.order} />
  }

  const stockRecovered = event.remainingTickets > 0

  return (
    <article className="relative overflow-hidden rounded-[1.75rem] border border-white/10 bg-zinc-900/80 p-5 shadow-glow backdrop-blur-xl sm:p-7">
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-orange-400/80 to-transparent" />

      <header>
        <div className="flex items-center justify-between gap-3">
          <span className="inline-flex items-center gap-2 rounded-full border border-red-400/20 bg-red-500/10 px-3 py-1.5 text-[11px] font-black uppercase tracking-[0.18em] text-red-300">
            <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-red-400" />
            Flash sale
          </span>
          <span className="text-xs font-semibold text-zinc-600">EVENT #{event.id}</span>
        </div>
        <h1 className="mt-5 text-3xl font-black leading-[1.08] tracking-tight text-white">
          {event.name}
        </h1>
        <p className="mt-3 text-sm leading-6 text-zinc-400">
          Một lượt bấm. Một cơ hội. Vé được giữ theo thứ tự hệ thống xử lý.
        </p>
      </header>

      <StockBar remaining={event.remainingTickets} total={event.totalTickets} />

      {!saleOpen && status === RESERVATION_STATUS.IDLE && (
        <CountdownToSale targetDate={event.startSaleAt} onComplete={handleSaleOpen} />
      )}

      {status === RESERVATION_STATUS.IDLE && (
        <MainButton
          onClick={onReserve}
          disabled={!saleOpen || event.remainingTickets <= 0}
          pulse={saleOpen && event.remainingTickets > 0}
        >
          <span aria-hidden="true">🎟</span>
          {!saleOpen
            ? 'Chưa đến giờ mở bán'
            : event.remainingTickets <= 0
              ? 'Vé đang tạm hết'
              : 'Giành vé ngay'}
        </MainButton>
      )}

      {status === RESERVATION_STATUS.RESERVING && (
        <StatusMessage icon={<Spinner />} title="Đang giành lượt cho bạn..." tone="amber">
          Đừng đóng trang — hệ thống đang kiểm tra tồn kho theo thời gian thực.
        </StatusMessage>
      )}

      {status === RESERVATION_STATUS.POLLING && (
        <StatusMessage icon={<Spinner />} title="Yêu cầu đã vào hàng đợi" tone="violet">
          <p>{reservation.message}</p>
          <p className="mt-2 tabular-nums text-xs font-semibold text-violet-300">
            Đang kiểm tra {reservation.pollAttempts}/15
          </p>
        </StatusMessage>
      )}

      {status === RESERVATION_STATUS.QUEUED && (
        <>
          <StatusMessage icon="⌛" title="Đang xếp hàng xử lý..." tone="violet">
            Hàng đợi đang đông. Yêu cầu của bạn vẫn còn nguyên, hãy kiểm tra lại.
          </StatusMessage>
          <MainButton onClick={onRetryPoll}>Thử kiểm tra lại</MainButton>
        </>
      )}

      {status === RESERVATION_STATUS.RESERVED && reservation.order && (
        <ReservationPanel
          order={reservation.order}
          paymentPending={reservation.paymentPending}
          paymentError={reservation.paymentError}
          onPay={onPay}
          onExpire={onExpire}
        />
      )}

      {status === RESERVATION_STATUS.SOLD_OUT && (
        <>
          <StatusMessage icon="😢" title={stockRecovered ? 'Vừa có vé quay lại!' : 'Hết vé rồi'} tone="red">
            {stockRecovered
              ? 'Một vé vừa được trả về kho. Bạn có thể thử lại ngay.'
              : 'Có người có thể chưa thanh toán. Trang sẽ tự cập nhật nếu vé quay lại kho.'}
          </StatusMessage>
          {stockRecovered && <MainButton onClick={onReset}>Thử giành vé lại</MainButton>}
        </>
      )}

      {status === RESERVATION_STATUS.ALREADY_OWNED && (
        <StatusMessage icon="🎫" title="Bạn đã có vé cho sự kiện này" tone="amber">
          Quy định mỗi người chỉ được giữ một vé. Dùng nút “Đổi user” để mô phỏng một người khác.
        </StatusMessage>
      )}

      {status === RESERVATION_STATUS.RATE_LIMITED && (
        <StatusMessage icon="✋" title="Bạn thao tác hơi nhanh" tone="amber">
          Tối đa 5 yêu cầu mỗi giây. Nút mua sẽ sẵn sàng lại sau giây lát.
        </StatusMessage>
      )}

      {status === RESERVATION_STATUS.EXPIRED && (
        <>
          <StatusMessage icon="⌛" title="Hết hạn giữ chỗ" tone="red">
            Vé đã được trả về kho. Bạn có thể bắt đầu lại khi tồn kho cập nhật.
          </StatusMessage>
          <MainButton onClick={onReset} disabled={event.remainingTickets <= 0}>
            {event.remainingTickets > 0 ? 'Thử lại từ đầu' : 'Đang chờ vé về kho...'}
          </MainButton>
        </>
      )}

      {status === RESERVATION_STATUS.ERROR && (
        <>
          <StatusMessage icon="!" title="Có lỗi xảy ra" tone="red">
            {reservation.message || 'Hệ thống chưa thể xử lý yêu cầu này.'}
          </StatusMessage>
          <button
            type="button"
            onClick={onReset}
            className="mt-4 w-full rounded-xl border border-white/10 px-4 py-3 text-sm font-bold text-zinc-200 transition hover:bg-white/5 focus:outline-none focus-visible:ring-2 focus-visible:ring-orange-300"
          >
            Quay lại
          </button>
        </>
      )}

      <footer className="mt-6 flex items-center justify-center gap-2 border-t border-white/[0.06] pt-4 text-[11px] text-zinc-600">
        <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
        Tồn kho được đồng bộ trực tiếp mỗi 3 giây
      </footer>
    </article>
  )
}
