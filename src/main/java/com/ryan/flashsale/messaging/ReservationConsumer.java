package com.ryan.flashsale.messaging;

import com.ryan.flashsale.config.RabbitConfig;
import com.ryan.flashsale.dto.ReservationMessage;
import com.ryan.flashsale.entity.Order;
import com.ryan.flashsale.entity.OrderStatus;
import com.ryan.flashsale.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Consumer ghi đơn chạy nền (Ngày 4).
 *
 * RabbitMQ giao message theo AT-LEAST-ONCE → có thể giao TRÙNG
 * (consumer xử lý xong nhưng ack thất bại → broker giao lại).
 * Chống trùng bằng IDEMPOTENCY: unique constraint trên orders.reservation_id
 * — DB là chốt chặn cuối cùng, kể cả 2 consumer xử lý cùng lúc.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationConsumer {

    private final OrderRepository orderRepository;

    @Value("${app.reservation-ttl-seconds:600}")
    private long reservationTtlSeconds;

    @RabbitListener(queues = RabbitConfig.QUEUE, autoStartup = "${app.consumer-enabled:true}")
    public void onReservationCreated(ReservationMessage msg) {
        // Ngày 7: payload "độc" để demo retry + DLQ —
        // reserve với X-User-Id: poison -> fail 3 lần (1s, 2s) -> rơi vào DLQ
        if ("poison".equals(msg.userId())) {
            log.warn("Poison message received (attempt will fail): {}", msg.reservationId());
            throw new IllegalStateException("Poison message demo - always fails");
        }

        // Check-trước cho đường trùng phổ biến (đỡ tốn 1 insert fail + stacktrace)
        if (orderRepository.existsByReservationId(msg.reservationId())) {
            log.info("Duplicate message skipped (already exists): reservationId={}", msg.reservationId());
            return;
        }
        try {
            Order order = Order.builder()
                    .userId(msg.userId())
                    .eventId(msg.eventId())
                    .status(OrderStatus.RESERVED)
                    .reservationId(msg.reservationId())
                    .createdAt(msg.reservedAt())
                    .expiresAt(msg.reservedAt().plusSeconds(reservationTtlSeconds))
                    .build();
            // saveAndFlush: 1 INSERT duy nhất, tự atomic — không cần @Transactional bao ngoài.
            // (Nếu bọc @Transactional rồi catch bên trong, transaction đã bị đánh dấu
            //  rollback-only → commit fail → message bị requeue vô hạn.)
            orderRepository.saveAndFlush(order);
            log.info("Order created from queue: reservationId={}, user={}, event={}",
                    msg.reservationId(), msg.userId(), msg.eventId());
        } catch (DataIntegrityViolationException e) {
            // Race giữa exists-check và insert (2 message trùng xử lý song song)
            // → unique constraint chặn → bỏ qua êm, KHÔNG ném lại để tránh requeue loop.
            log.info("Duplicate message skipped (unique constraint): reservationId={}", msg.reservationId());
        }
    }
}
