package com.ryan.flashsale.service;

import com.ryan.flashsale.entity.OrderStatus;
import com.ryan.flashsale.repository.EventRepository;
import com.ryan.flashsale.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Kho vé trên Redis. Ngày 3: DECR/INCR đơn lệnh.
 * Ngày 6: nâng cấp lên Lua script — atomic cả KHỐI logic
 * (check 1-vé/người + check stock + trừ + ghi nhận người mua).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StringRedisTemplate redis;
    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final DefaultRedisScript<Long> reserveScript;

    public static final long RESERVE_SOLD_OUT = -1;
    public static final long RESERVE_ALREADY_PURCHASED = -2;

    public static String stockKey(Long eventId) {
        return "stock:event:" + eventId;
    }

    public static String purchasedKey(Long eventId) {
        return "purchased:event:" + eventId;
    }

    /**
     * Ngày 6: reserve atomic bằng Lua.
     * Trả về >= 0 (còn lại sau trừ) | -1 (hết vé) | -2 (user đã mua).
     */
    public long reserveAtomic(Long eventId, String userId) {
        Long result = redis.execute(reserveScript,
                List.of(stockKey(eventId), purchasedKey(eventId)),
                userId);
        return result == null ? RESERVE_SOLD_OUT : result;
    }

    /**
     * Trả vé về kho + gỡ user khỏi danh sách đã-mua.
     * Dùng cho compensation (publish fail) và job expire.
     */
    public void returnTicket(Long eventId, String userId) {
        redis.opsForValue().increment(stockKey(eventId));
        redis.opsForSet().remove(purchasedKey(eventId), userId);
    }

    /** Đọc stock hiện tại (null nếu key chưa tồn tại). */
    public Long get(Long eventId) {
        String s = redis.opsForValue().get(stockKey(eventId));
        return s == null ? null : Long.parseLong(s);
    }

    /**
     * Nạp kho + dựng lại purchased set từ DB (nguồn sự thật).
     * stock = totalTickets - số đơn còn hiệu lực (chống oversell khi restart).
     */
    public void syncFromDb() {
        eventRepository.findAll().forEach(event -> {
            List<com.ryan.flashsale.entity.Order> activeOrders =
                    orderRepository.findByEventIdAndStatusIn(
                            event.getId(), List.of(OrderStatus.RESERVED, OrderStatus.PAID));
            long stock = Math.max(0, event.getTotalTickets() - activeOrders.size());
            redis.opsForValue().set(stockKey(event.getId()), String.valueOf(stock));

            redis.delete(purchasedKey(event.getId()));
            if (!activeOrders.isEmpty()) {
                String[] userIds = activeOrders.stream()
                        .map(com.ryan.flashsale.entity.Order::getUserId)
                        .distinct()
                        .toArray(String[]::new);
                redis.opsForSet().add(purchasedKey(event.getId()), userIds);
            }
            log.info("Stock synced: event {} -> {} (total {}, activeOrders {})",
                    event.getId(), stock, event.getTotalTickets(), activeOrders.size());
        });
    }
}
