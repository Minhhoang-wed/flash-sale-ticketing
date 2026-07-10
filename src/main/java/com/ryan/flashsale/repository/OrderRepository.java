package com.ryan.flashsale.repository;

import com.ryan.flashsale.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
