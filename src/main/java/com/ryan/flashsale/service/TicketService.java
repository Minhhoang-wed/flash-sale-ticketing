package com.ryan.flashsale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryan.flashsale.config.RabbitConfig;
import com.ryan.flashsale.dto.EventResponse;
import com.ryan.flashsale.dto.ReservationMessage;
import com.ryan.flashsale.dto.ReserveResult;
import com.ryan.flashsale.entity.Event;
import com.ryan.flashsale.entity.Order;
import com.ryan.flashsale.entity.OrderStatus;
import com.ryan.flashsale.exception.AlreadyPurchasedException;
import com.ryan.flashsale.exception.InvalidOrderStateException;
import com.ryan.flashsale.exception.NotFoundException;
import com.ryan.flashsale.exception.SoldOutException;
import com.ryan.flashsale.repository.EventRepository;
import com.ryan.flashsale.repository.OrderRepository;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class TicketService {

    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final AmqpAdmin amqpAdmin;

    /**
     * Self-injection: gọi this.reservePessimistic() trực tiếp sẽ BYPASS proxy
     * của Spring → @Transactional không chạy. Gọi qua self thì đi qua proxy.
     */
    private final TicketService self;

    /** naive | pessimistic | optimistic | redis — đổi qua env RESERVE_STRATEGY */
    @Getter
    @Value("${app.reserve-strategy:redis}")
    private String reserveStrategy;

    @Value("${app.event-cache-ttl-seconds:60}")
    private long eventCacheTtlSeconds;

    /** Hạn giữ chỗ (Ngày 5). Demo nhanh: RESERVATION_TTL_SECONDS=60 */
    @Getter
    @Value("${app.reservation-ttl-seconds:600}")
    private long reservationTtlSeconds;

    /** Đếm số lần optimistic lock bị conflict phải retry (so sánh Ngày 2) */
    private final AtomicLong optimisticRetries = new AtomicLong();

    public TicketService(EventRepository eventRepository,
                         OrderRepository orderRepository,
                         StockService stockService,
                         StringRedisTemplate redis,
                         ObjectMapper objectMapper,
                         RabbitTemplate rabbitTemplate,
                         AmqpAdmin amqpAdmin,
                         @Lazy TicketService self) {
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.stockService = stockService;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.amqpAdmin = amqpAdmin;
        this.self = self;
    }

    // ==================== GET EVENT (cache-aside, Ngày 3) ====================

    private static String eventCacheKey(Long id) {
        return "event:" + id;
    }

    /**
     * CACHE-ASIDE: đọc Redis trước → miss thì query DB rồi SET ... EX 60.
     * Số vé còn lại luôn lấy live từ stock key (DB không còn trừ vé ở pha reserve).
     */
    @SneakyThrows
    public EventResponse getEventResponse(Long eventId) {
        String cached = redis.opsForValue().get(eventCacheKey(eventId));
        EventResponse base;
        if (cached != null) {
            base = objectMapper.readValue(cached, EventResponse.class);
        } else {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
            base = EventResponse.from(event);
            redis.opsForValue().set(eventCacheKey(eventId),
                    objectMapper.writeValueAsString(base),
                    Duration.ofSeconds(eventCacheTtlSeconds));
            log.info("Cache MISS event {} -> cached {}s", eventId, eventCacheTtlSeconds);
        }
        Long stock = stockService.get(eventId);
        return stock == null ? base : base.withRemaining(stock.intValue());
    }

    /** Invalidation: gọi khi event bị sửa (hiện dùng trong resetDemo). */
    public void evictEventCache(Long eventId) {
        redis.delete(eventCacheKey(eventId));
    }

    @Transactional(readOnly = true)
    public Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
    }

    // ==================== RESERVE ====================

    public ReserveResult reserve(Long eventId, String userId) {
        return switch (reserveStrategy) {
            case "pessimistic" -> sync(self.reservePessimistic(eventId, userId));
            case "optimistic" -> sync(reserveOptimistic(eventId, userId));
            case "redis" -> reserveRedis(eventId, userId);
            default -> sync(self.reserveNaive(eventId, userId));
        };
    }

    private static ReserveResult sync(Order order) {
        return new ReserveResult(order.getReservationId(), order);
    }

    /**
     * CÁCH 3 — REDIS ATOMIC COUNTER (Ngày 3):
     * DECR là atomic → không thể có 2 request cùng "thấy còn 1 vé".
     * DB KHÔNG bị chạm ở pha từ chối; pha thành công DB chỉ ghi đơn.
     *
     * DECR trả về < 0  → hết vé: INCR cộng bù (trả lại vé ảo vừa trừ lố) + 409.
     * DECR trả về >= 0 → giữ chỗ thành công: tạo đơn. Nếu tạo đơn ném exception
     * → COMPENSATION: INCR trả vé rồi ném tiếp (pattern giống avatar upload).
     */
    private ReserveResult reserveRedis(Long eventId, String userId) {
        // Ngày 6: Lua script — check 1-vé/người + stock + DECR + SADD, atomic cả khối
        long result = stockService.reserveAtomic(eventId, userId);
        if (result == StockService.RESERVE_ALREADY_PURCHASED) {
            throw new AlreadyPurchasedException(
                    "User " + userId + " already reserved a ticket for event " + eventId);
        }
        if (result == StockService.RESERVE_SOLD_OUT) {
            throw new SoldOutException("Event " + eventId + " is sold out");
        }
        // Ngày 4: DB không được chạm ở pha reserve nữa —
        // chỉ publish message, consumer sẽ ghi đơn ở background.
        String reservationId = UUID.randomUUID().toString();
        try {
            ReservationMessage msg = new ReservationMessage(
                    reservationId, userId, eventId, Instant.now());
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, msg);
            return new ReserveResult(reservationId, null);
        } catch (RuntimeException e) {
            stockService.returnTicket(eventId, userId);
            log.warn("Publish failed, compensated stock for event {}: {}", eventId, e.getMessage());
            throw e;
        }
    }

    /**
     * CÁCH 0 — NAIVE (Ngày 1, giữ để demo):
     * read → check → write không atomic → oversell khi concurrent.
     */
    @Transactional
    public Order reserveNaive(Long eventId, String userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

        int remaining = event.getRemainingTickets();
        if (remaining <= 0) {
            throw new SoldOutException("Event " + eventId + " is sold out");
        }
        event.setRemainingTickets(remaining - 1);
        eventRepository.save(event);

        return createOrder(eventId, userId);
    }

    /**
     * CÁCH 1 — PESSIMISTIC LOCK (Ngày 2): SELECT ... FOR UPDATE serialize
     * read-check-write trên row event.
     */
    @Transactional
    public Order reservePessimistic(Long eventId, String userId) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

        if (event.getRemainingTickets() <= 0) {
            throw new SoldOutException("Event " + eventId + " is sold out");
        }
        event.setRemainingTickets(event.getRemainingTickets() - 1);

        return createOrder(eventId, userId);
    }

    /**
     * CÁCH 2 — OPTIMISTIC LOCK (Ngày 2): version + retry tối đa 3 lần.
     */
    private Order reserveOptimistic(Long eventId, String userId) {
        final int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

            if (event.getRemainingTickets() <= 0) {
                throw new SoldOutException("Event " + eventId + " is sold out");
            }

            int updated = eventRepository.tryDecrementWithVersion(eventId, event.getVersion());
            if (updated == 1) {
                return createOrder(eventId, userId);
            }
            optimisticRetries.incrementAndGet();
            log.debug("Optimistic conflict on event {} (attempt {}/{})", eventId, attempt, maxAttempts);
            sleepBriefly();
        }
        throw new SoldOutException(
                "Could not reserve after " + maxAttempts + " attempts (contention quá cao)");
    }

    // ==================== PAY ====================

    /**
     * PAY (Ngày 5): check-and-update trong 1 câu UPDATE có điều kiện.
     * KHÔNG dùng read-check-write kiểu cũ — sẽ race với job expire y hệt
     * bài oversell Ngày 2. UPDATE ... WHERE status='RESERVED' AND chưa quá hạn:
     * pay và expire chỉ MỘT bên thắng nhờ row lock của DB.
     */
    public Order pay(Long orderId) {
        int updated = orderRepository.markPaidIfReservedAndNotExpired(
                orderId, Instant.now(), OrderStatus.RESERVED, OrderStatus.PAID);
        if (updated == 1) {
            return orderRepository.findById(orderId).orElseThrow();
        }
        // 0 row bị update → chẩn đoán lý do để trả lỗi đúng
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        if (order.getStatus() == OrderStatus.RESERVED) {
            throw new InvalidOrderStateException(
                    "Order " + orderId + " reservation expired at " + order.getExpiresAt());
        }
        throw new InvalidOrderStateException(
                "Order " + orderId + " is " + order.getStatus() + ", expected RESERVED");
    }

    // ==================== debug/demo helpers ====================

    public long getOptimisticRetries() {
        return optimisticRetries.get();
    }

    @Transactional
    public void resetDemo() {
        orderRepository.deleteAllInBatch();
        eventRepository.findAll().forEach(e -> {
            e.setRemainingTickets(e.getTotalTickets());
            e.setVersion(0);
            evictEventCache(e.getId());
        });
        optimisticRetries.set(0);
        try {
            amqpAdmin.purgeQueue(RabbitConfig.QUEUE, false);
        } catch (RuntimeException e) {
            log.warn("Could not purge queue: {}", e.getMessage());
        }
        stockService.syncFromDb();
        log.info("Demo reset: stock refilled (DB + Redis), orders wiped, cache + queue purged");
    }

    private Order createOrder(Long eventId, String userId) {
        Instant now = Instant.now();
        Order order = Order.builder()
                .userId(userId)
                .eventId(eventId)
                .status(OrderStatus.RESERVED)
                .reservationId(UUID.randomUUID().toString())
                .createdAt(now)
                .expiresAt(now.plusSeconds(reservationTtlSeconds))
                .build();
        return orderRepository.save(order);
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(5, 20));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
