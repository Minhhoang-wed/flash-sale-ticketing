import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { initialReservationState, RESERVATION_STATUS } from '../hooks/useReservation'
import EventCard from './EventCard'

const event = {
  id: 1,
  name: 'Concert ABC - Flash Sale',
  totalTickets: 100,
  remainingTickets: 87,
  startSaleAt: '2020-01-01T00:00:00Z',
}

const defaultProps = {
  event,
  reservation: initialReservationState,
  onReserve: vi.fn(),
  onPay: vi.fn(),
  onRetryPoll: vi.fn(),
  onExpire: vi.fn(),
  onReset: vi.fn(),
}

describe('EventCard', () => {
  it('hiện tồn kho và cho mua khi sale đã mở', async () => {
    const onReserve = vi.fn()
    const user = userEvent.setup()
    render(<EventCard {...defaultProps} onReserve={onReserve} />)

    expect(screen.getByText('Concert ABC - Flash Sale')).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '87')

    const button = screen.getByRole('button', { name: 'Giành vé ngay' })
    expect(button).toBeEnabled()
    await user.click(button)
    expect(onReserve).toHaveBeenCalledTimes(1)
  })

  it('mở nút thử lại khi vé quay về kho sau SOLD_OUT', () => {
    render(
      <EventCard
        {...defaultProps}
        reservation={{ ...initialReservationState, status: RESERVATION_STATUS.SOLD_OUT }}
      />,
    )

    expect(screen.getByText('Vừa có vé quay lại!')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Thử giành vé lại' })).toBeEnabled()
  })

  it('hiện vé điện tử ở trạng thái PAID', () => {
    render(
      <EventCard
        {...defaultProps}
        reservation={{
          ...initialReservationState,
          status: RESERVATION_STATUS.PAID,
          order: {
            id: 12,
            userId: 'user-abc123',
            reservationId: 'reservation-123456789',
            status: 'PAID',
          },
        }}
      />,
    )

    expect(screen.getByText('Thanh toán thành công')).toBeInTheDocument()
    expect(screen.getByText('user-abc123')).toBeInTheDocument()
    expect(screen.getByText('#00012')).toBeInTheDocument()
  })
})
