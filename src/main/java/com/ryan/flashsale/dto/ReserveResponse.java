package com.ryan.flashsale.dto;

/**
 * Response của POST /reserve.
 * - Async (202): orderId = null, status = PENDING → client poll
 *   GET /api/orders/by-reservation/{reservationId}
 * - Sync (201): orderId có ngay, status = RESERVED
 */
public record ReserveResponse(String reservationId, Long orderId, String status) {
}
