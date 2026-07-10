package com.ryan.flashsale.controller;

import com.ryan.flashsale.dto.EventResponse;
import com.ryan.flashsale.dto.ReserveResponse;
import com.ryan.flashsale.dto.ReserveResult;
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
    @Operation(summary = "Thông tin sự kiện + số vé còn lại (cache-aside qua Redis)")
    public EventResponse getEvent(@PathVariable Long id) {
        return ticketService.getEventResponse(id);
    }

    @PostMapping("/{id}/reserve")
    @Operation(summary = "Giữ chỗ 1 vé. Strategy redis → 202 + reservationId (poll đơn sau); "
            + "strategy khác → 201 + đơn tạo ngay")
    public ResponseEntity<ReserveResponse> reserve(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {
        ReserveResult result = ticketService.reserve(id, userId);
        if (result.isAsync()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new ReserveResponse(result.reservationId(), null, "PENDING"));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ReserveResponse(result.reservationId(),
                        result.order().getId(),
                        result.order().getStatus().name()));
    }
}
