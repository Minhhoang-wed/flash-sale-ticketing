package com.ryan.flashsale.controller;

import com.ryan.flashsale.repository.EventRepository;
import com.ryan.flashsale.repository.OrderRepository;
import com.ryan.flashsale.service.StockService;
import com.ryan.flashsale.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint phục vụ demo/load test — không dùng cho production.
 */
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Tag(name = "Debug (load test)")
public class DebugController {

    private final TicketService ticketService;
    private final OrderRepository orderRepository;
    private final EventRepository eventRepository;
    private final StockService stockService;

    @PostMapping("/reset")
    @Operation(summary = "Reset stock (DB + Redis), xóa hết order, xóa cache, reset metrics")
    public Map<String, Object> reset() {
        ticketService.resetDemo();
        return Map.of("status", "reset done");
    }

    @GetMapping("/metrics")
    @Operation(summary = "Chiến lược đang chạy + tổng order + optimistic retry + stock Redis")
    public Map<String, Object> metrics() {
        Map<Long, Long> redisStock = new HashMap<>();
        eventRepository.findAll().forEach(e -> redisStock.put(e.getId(), stockService.get(e.getId())));
        return Map.of(
                "strategy", ticketService.getReserveStrategy(),
                "totalOrders", orderRepository.count(),
                "optimisticRetries", ticketService.getOptimisticRetries(),
                "redisStock", redisStock
        );
    }
}
