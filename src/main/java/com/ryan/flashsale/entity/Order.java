package com.ryan.flashsale.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * "order" là reserved keyword trong SQL nên đặt tên bảng là "orders".
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /** Unique constraint = chốt chặn idempotency (Ngày 4). */
    @Column(nullable = false, unique = true)
    private String reservationId;

    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Hạn giữ chỗ (Ngày 5): RESERVED chỉ sống đến thời điểm này.
     * Quá hạn → job set EXPIRED + trả vé về kho Redis.
     */
    @Column
    private Instant expiresAt;
}
