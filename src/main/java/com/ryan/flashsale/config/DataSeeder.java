package com.ryan.flashsale.config;

import com.ryan.flashsale.entity.Event;
import com.ryan.flashsale.repository.EventRepository;
import com.ryan.flashsale.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final EventRepository eventRepository;
    private final StockService stockService;

    @Override
    public void run(String... args) {
        if (eventRepository.count() == 0) {
            Event event = Event.builder()
                    .name("Concert ABC - Flash Sale")
                    .totalTickets(100)
                    .remainingTickets(100)
                    .startSaleAt(Instant.now())
                    .build();
            eventRepository.save(event);
            log.info("Seeded event id={} with {} tickets", event.getId(), event.getTotalTickets());
        } else {
            log.info("Seed skipped - events already exist");
        }
        // Ngày 3: nạp kho vé vào Redis mỗi lần app start
        stockService.syncFromDb();
    }
}
