package com.ryan.flashsale.service;

import com.ryan.flashsale.entity.Event;
import com.ryan.flashsale.entity.Order;
import com.ryan.flashsale.entity.OrderStatus;
import com.ryan.flashsale.exception.InvalidOrderStateException;
import com.ryan.flashsale.exception.NotFoundException;
import com.ryan.flashsale.exception.SoldOutException;
import com.ryan.flashsale.repository.EventRepository;
import com.ryan.flashsale.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));
    }

    /**
     * BẢN NGÂY THƠ (Ngày 1) — cố tình có race condition:
     * read-check-write không atomic. Hai request đọc cùng lúc
     * remainingTickets = 1 thì cả hai đều trừ được → oversell.
     * Ngày 2 sẽ chứng minh bằng load test, sau đó mới sửa.
     */
    @Transactional
    public Order reserve(Long eventId, String userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found: " + eventId));

        // 1. READ
        int remaining = event.getRemainingTickets();

        // 2. CHECK
        if (remaining <= 0) {
            throw new SoldOutException("Event " + eventId + " is sold out");
        }

        // 3. WRITE (không atomic với bước 1-2!)
        event.setRemainingTickets(remaining - 1);
        eventRepository.save(event);

        Order order = Order.builder()
                .userId(userId)
                .eventId(eventId)
                .status(OrderStatus.RESERVED)
                .reservationId(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .build();
        return orderRepository.save(order);
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
}
