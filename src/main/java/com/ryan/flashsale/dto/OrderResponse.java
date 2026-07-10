package com.ryan.flashsale.dto;

import com.ryan.flashsale.entity.Order;
import com.ryan.flashsale.entity.OrderStatus;

import java.time.Instant;

public record OrderResponse(
        Long id,
        String userId,
        Long eventId,
        OrderStatus status,
        String reservationId,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getEventId(),
                order.getStatus(),
                order.getReservationId(),
                order.getCreatedAt()
        );
    }
}
