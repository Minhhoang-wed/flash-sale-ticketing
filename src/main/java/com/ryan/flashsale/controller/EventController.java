package com.ryan.flashsale.controller;

import com.ryan.flashsale.dto.EventResponse;
import com.ryan.flashsale.dto.OrderResponse;
import com.ryan.flashsale.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events")
public class EventController {

    private final TicketService ticketService;

    @GetMapping("/{id}")
    @Operation(summary = "Thông tin sự kiện + số vé còn lại")
    public EventResponse getEvent(@PathVariable Long id) {
        return EventResponse.from(ticketService.getEvent(id));
    }

    @PostMapping("/{id}/reserve")
    @Operation(summary = "Giữ chỗ 1 vé (naive - Ngày 1)")
    public ResponseEntity<OrderResponse> reserve(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {
        OrderResponse order = OrderResponse.from(ticketService.reserve(id, userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
}
