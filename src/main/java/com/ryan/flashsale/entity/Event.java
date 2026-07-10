package com.ryan.flashsale.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int totalTickets;

    /**
     * Counter đơn giản thay vì tạo từng row Ticket.
     * Đây chính là shared mutable state — nguồn gốc race condition (Ngày 2).
     */
    @Column(nullable = false)
    private int remainingTickets;

    @Column(nullable = false)
    private Instant startSaleAt;
}
