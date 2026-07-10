package com.ryan.flashsale.controller;

import com.ryan.flashsale.repository.OrderRepository;
import com.ryan.flashsale.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint phục vụ demo/load test Ngày 2 — không dùng cho production.
 */
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Tag(name = "Debug (load test)")
public class DebugController {

    private final TicketService ticketService;
    private final OrderRepository orderRepository;

    @PostMapping("/reset")
    @Operation(summary = "Reset stock về 100, xóa hết order, reset metrics")
    public Map<String, Object> reset() {
        ticketService.resetDemo();
        return Map.of("status", "reset done");
    }

    @GetMapping("/metrics")
    @Operation(summary = "Chiến lược đang chạy + tổng số order + số lần optimistic retry")
    public Map<String, Object> metrics() {
        return Map.of(
                "strategy", ticketService.getReserveStrategy(),
                "totalOrders", orderRepository.count(),
                "optimisticRetries", ticketService.getOptimisticRetries()
        );
    }
}
