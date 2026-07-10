package com.ryan.flashsale.dto;

import java.time.Instant;

/** Payload gửi qua RabbitMQ khi giữ chỗ thành công (Ngày 4). */
public record ReservationMessage(
        String reservationId,
        String userId,
        Long eventId,
        Instant reservedAt
) {
}
