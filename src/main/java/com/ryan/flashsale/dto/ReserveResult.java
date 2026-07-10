package com.ryan.flashsale.dto;

import com.ryan.flashsale.entity.Order;

/**
 * Kết quả reserve nội bộ.
 * order == null → async (redis strategy): đơn sẽ được consumer tạo sau.
 */
public record ReserveResult(String reservationId, Order order) {

    public boolean isAsync() {
        return order == null;
    }
}
