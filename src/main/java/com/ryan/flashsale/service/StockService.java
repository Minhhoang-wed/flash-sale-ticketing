package com.ryan.flashsale.service;

import com.ryan.flashsale.entity.OrderStatus;
import com.ryan.flashsale.repository.EventRepository;
import com.ryan.flashsale.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Kho vé trên Redis — "chuyển kho ra cửa" (Ngày 3).
 * DECR/INCR là ATOMIC: Redis single-threaded xử lý từng lệnh trọn vẹn,
 * không có chuyện 2 client cùng đọc 1 giá trị rồi cùng ghi đè nhau.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StringRedisTemplate redis;
    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;

    public static String stockKey(Long eventId) {
        return "stock:event:" + eventId;
    }

    /** DECR nguyên tử. Trả về giá trị SAU khi trừ (có thể âm). */
    public long decrement(Long eventId) {
        Long v = redis.opsForValue().decrement(stockKey(eventId));
        return v == null ? -1 : v;
    }

    /** INCR cộng bù — dùng khi trừ lố hoặc compensation lúc tạo đơn thất bại. */
    public void increment(Long eventId) {
        redis.opsForValue().increment(stockKey(eventId));
    }

    /** Đọc stock hiện tại (null nếu key chưa tồn tại). */
    public Long get(Long eventId) {
        String s = redis.opsForValue().get(stockKey(eventId));
        return s == null ? null : Long.parseLong(s);
    }

    /**
     * Nạp kho từ DB: stock = totalTickets - số đơn còn hiệu lực.
     * KHÔNG dùng thẳng totalTickets: nếu app restart giữa chừng đợt sale,
     * set lại 100 trong khi đã có đơn → oversell qua đường restart.
     */
    public void syncFromDb() {
        eventRepository.findAll().forEach(event -> {
            long activeOrders = orderRepository.countByEventIdAndStatusIn(
                    event.getId(), List.of(OrderStatus.RESERVED, OrderStatus.PAID));
            long stock = Math.max(0, event.getTotalTickets() - activeOrders);
            redis.opsForValue().set(stockKey(event.getId()), String.valueOf(stock));
            log.info("Stock synced: event {} -> {} (total {}, activeOrders {})",
                    event.getId(), stock, event.getTotalTickets(), activeOrders);
        });
    }
}
