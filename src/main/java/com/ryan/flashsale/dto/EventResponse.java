package com.ryan.flashsale.dto;

import com.ryan.flashsale.entity.Event;

import java.time.Instant;

public record EventResponse(
        Long id,
        String name,
        int totalTickets,
        int remainingTickets,
        Instant startSaleAt
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getTotalTickets(),
                event.getRemainingTickets(),
                event.getStartSaleAt()
        );
    }
}
