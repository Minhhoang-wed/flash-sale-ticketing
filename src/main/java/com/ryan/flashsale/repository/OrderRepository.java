package com.ryan.flashsale.repository;

import com.ryan.flashsale.entity.Order;
import com.ryan.flashsale.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface OrderRepository extends JpaRepository<Order, Long> {

    long countByEventIdAndStatusIn(Long eventId, Collection<OrderStatus> statuses);
}
