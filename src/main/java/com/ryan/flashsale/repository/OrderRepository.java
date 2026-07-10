package com.ryan.flashsale.repository;

import com.ryan.flashsale.entity.Order;
import com.ryan.flashsale.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    long countByEventIdAndStatusIn(Long eventId, Collection<OrderStatus> statuses);

    boolean existsByReservationId(String reservationId);

    Optional<Order> findByReservationId(String reservationId);

    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, Instant cutoff);

    /**
     * PAY có điều kiện (Ngày 5): chỉ thắng khi đơn còn RESERVED và CHƯA quá hạn.
     * Check-and-update trong 1 câu UPDATE — DB row lock đảm bảo pay và expire
     * không thể cùng thắng (optimistic style trên cột status).
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update Order o
               set o.status = :paid
             where o.id = :id
               and o.status = :reserved
               and o.expiresAt > :now
            """)
    int markPaidIfReservedAndNotExpired(@Param("id") Long id,
                                        @Param("now") Instant now,
                                        @Param("reserved") OrderStatus reserved,
                                        @Param("paid") OrderStatus paid);

    /**
     * EXPIRE có điều kiện: chỉ thắng khi đơn còn RESERVED.
     * Trả về 1 → caller mới được INCR trả vé (tránh trả vé cho đơn đã PAID).
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update Order o
               set o.status = :expired
             where o.id = :id
               and o.status = :reserved
            """)
    int markExpiredIfReserved(@Param("id") Long id,
                              @Param("reserved") OrderStatus reserved,
                              @Param("expired") OrderStatus expired);
}
