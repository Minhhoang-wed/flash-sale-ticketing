package com.ryan.flashsale.controller;

import com.ryan.flashsale.dto.OrderResponse;
import com.ryan.flashsale.exception.NotFoundException;
import com.ryan.flashsale.repository.OrderRepository;
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
    private final OrderRepository orderRepository;

    @PostMapping("/{id}/pay")
    @Operation(summary = "Thanh toán: RESERVED → PAID")
    public OrderResponse pay(@PathVariable Long id) {
        return OrderResponse.from(ticketService.pay(id));
    }

    @GetMapping("/by-reservation/{reservationId}")
    @Operation(summary = "Poll trạng thái đơn theo reservationId. "
            + "404 = consumer chưa xử lý xong (client thử lại sau)")
    public OrderResponse byReservation(@PathVariable String reservationId) {
        return orderRepository.findByReservationId(reservationId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new NotFoundException(
                        "Order not created yet for reservation " + reservationId
                        + " (still processing?)"));
    }
}
