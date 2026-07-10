package com.ryan.flashsale.repository;

import com.ryan.flashsale.entity.Order;
import com.ryan.flashsale.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    long countByEventIdAndStatusIn(Long eventId, Collection<OrderStatus> statuses);

    boolean existsByReservationId(String reservationId);

    Optional<Order> findByReservationId(String reservationId);
}
