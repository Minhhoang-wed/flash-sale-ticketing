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

    /**
     * Version cho optimistic lock THỦ CÔNG (Ngày 2).
     * Cố ý KHÔNG dùng @Version của JPA: nếu dùng @Version thì Hibernate
     * tự check version trên MỌI update → chiến lược naive sẽ không còn
     * oversell được nữa, mất bài demo "before".
     */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private int version = 0;

    @Column(nullable = false)
    private Instant startSaleAt;
}
