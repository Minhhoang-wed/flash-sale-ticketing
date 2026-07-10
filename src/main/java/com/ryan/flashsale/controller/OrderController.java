package com.ryan.flashsale.controller;

import com.ryan.flashsale.dto.OrderResponse;
import com.ryan.flashsale.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderController {

    private final TicketService ticketService;

    @PostMapping("/{id}/pay")
    @Operation(summary = "Thanh toán: RESERVED → PAID")
    public OrderResponse pay(@PathVariable Long id) {
        return OrderResponse.from(ticketService.pay(id));
    }
}
