package com.ryan.flashsale.repository;

import com.ryan.flashsale.entity.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * PESSIMISTIC LOCK: sinh ra "SELECT ... FOR UPDATE".
     * Transaction nào giữ được row lock thì đi tiếp, các transaction khác
     * PHẢI CHỜ đến khi lock được nhả (commit/rollback) → serialize việc trừ vé.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    /**
     * OPTIMISTIC (thủ công): UPDATE có điều kiện version.
     * Nếu version đã bị transaction khác đổi → update 0 row → caller retry.
     * Điều kiện remainingTickets > 0 chặn luôn oversell ở tầng SQL.
     */
    @Transactional
    @Modifying
    @Query("""
            update Event e
               set e.remainingTickets = e.remainingTickets - 1,
                   e.version = e.version + 1
             where e.id = :id
               and e.version = :version
               and e.remainingTickets > 0
            """)
    int tryDecrementWithVersion(@Param("id") Long id, @Param("version") int version);
}
