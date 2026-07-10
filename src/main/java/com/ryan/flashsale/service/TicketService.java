package com.ryan.flashsale.service;

import com.ryan.flashsale.entity.Event;
import com.ryan.flashsale.entity.Order;
import com.ryan.flashsale.entity.OrderStatus;
import com.ryan.flashsale.exception.InvalidOrderStateException;
import com.ryan.flashsale.exception.NotFoundException;
import com.ryan.flashsale.exception.SoldOutException;
import com.ryan.flashsale.repository.EventRepository;
import com.ryan.flashsale.repository.OrderRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class TicketService {

    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;

    /**
     * Self-injection: gọi this.reservePessimistic() trực tiếp sẽ BYPASS proxy
     * của Spring → @Transactional không chạy. Gọi qua self thì đi qua proxy.
     */
    private final TicketService self;

    /** naive | pessimistic | optimistic — đổi qua env RESERVE_STRATEGY */
    @Getter
    @Value("${app.reserve-strategy:naive}")
    private String reserveStrategy;

    /** Đếm số lần optimistic lock bị conflict phải retry (để so sánh Ngày 2) */
    private final AtomicLong optimisticRetries = new AtomicLong();

    public TicketService(EventRepository eventRepository,
                         OrderRepository orderRepository,
                         @Lazy TicketService self) {
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.self = self;
    }

    @Transactional(readOnly = true)
    public Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
    }

    public Order reserve(Long eventId, String userId) {
        return switch (reserveStrategy) {
            case "pessimistic" -> self.reservePessimistic(eventId, userId);
            case "optimistic" -> reserveOptimistic(eventId, userId);
            default -> self.reserveNaive(eventId, userId);
        };
    }

    /**
     * CÁCH 0 — NAIVE (Ngày 1, giữ lại để demo "before"):
     * read → check → write không atomic. 2 request cùng đọc remaining = 1
     * → cả hai pass check → oversell.
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
     * CÁCH 1 — PESSIMISTIC LOCK:
     * SELECT ... FOR UPDATE giữ row lock đến hết transaction.
     * Mọi request khác phải XẾP HÀNG chờ → read-check-write trở thành atomic.
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
     * CÁCH 2 — OPTIMISTIC LOCK (thủ công, retry tối đa 3 lần):
     * Không lock khi đọc; lúc ghi mới check version. Conflict → retry.
     * KHÔNG có @Transactional bao ngoài: mỗi attempt là 1 transaction riêng
     * để lần đọc sau thấy version mới nhất.
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

            // version đã bị thread khác đổi → conflict
            optimisticRetries.incrementAndGet();
            log.debug("Optimistic conflict on event {} (attempt {}/{})", eventId, attempt, maxAttempts);
            sleepBriefly();
        }
        throw new SoldOutException(
                "Could not reserve after " + maxAttempts + " attempts (contention quá cao)");
    }

    @Transactional
    public Order pay(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.RESERVED) {
            throw new InvalidOrderStateException(
                    "Order " + orderId + " is " + order.getStatus() + ", expected RESERVED");
        }
        order.setStatus(OrderStatus.PAID);
        return orderRepository.save(order);
    }

    // ---- debug/demo helpers ----

    public long getOptimisticRetries() {
        return optimisticRetries.get();
    }

    @Transactional
    public void resetDemo() {
        orderRepository.deleteAllInBatch();
        eventRepository.findAll().forEach(e -> {
            e.setRemainingTickets(e.getTotalTickets());
            e.setVersion(0);
        });
        optimisticRetries.set(0);
        log.info("Demo reset: stock refilled, orders wiped, metrics zeroed");
    }

    private Order createOrder(Long eventId, String userId) {
        Order order = Order.builder()
                .userId(userId)
                .eventId(eventId)
                .status(OrderStatus.RESERVED)
                .reservationId(UUID.randomUUID().toString())
                .createdAt(Instant.now())
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
