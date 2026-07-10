package com.ryan.flashsale.service;

import com.ryan.flashsale.entity.Order;
import com.ryan.flashsale.entity.OrderStatus;
import com.ryan.flashsale.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Job hết hạn giữ chỗ (Ngày 5): quét đơn RESERVED quá hạn
 * → set EXPIRED + INCR trả vé về kho Redis.
 *
 * Race với pay: cả hai bên đều dùng UPDATE có điều kiện trên status.
 * Job chỉ INCR khi markExpiredIfReserved trả 1 — nếu pay vừa thắng trước
 * (status đã PAID) thì update 0 row → KHÔNG trả vé → không có chuyện
 * vé vừa bán vừa hoàn kho.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryJob {

    private final OrderRepository orderRepository;
    private final StockService stockService;

    @Scheduled(fixedDelayString = "${app.expire-scan-delay-ms:30000}")
    public void expireOverdueReservations() {
        List<Order> overdue = orderRepository.findByStatusAndExpiresAtBefore(
                OrderStatus.RESERVED, Instant.now());
        if (overdue.isEmpty()) {
            return;
        }
        int expired = 0;
        for (Order order : overdue) {
            int updated = orderRepository.markExpiredIfReserved(
                    order.getId(), OrderStatus.RESERVED, OrderStatus.EXPIRED);
            if (updated == 1) {
                // Thắng cuộc đua với pay → trả vé về kho + cho phép user mua lại
                stockService.returnTicket(order.getEventId(), order.getUserId());
                expired++;
            }
            // updated == 0: pay vừa thắng trước → bỏ qua, không trả vé
        }
        log.info("Expiry sweep: {} candidates, {} expired & returned to stock",
                overdue.size(), expired);
    }
}
