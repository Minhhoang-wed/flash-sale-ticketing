package com.ryan.flashsale.repository;

import com.ryan.flashsale.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}
