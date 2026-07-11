package com.ryan.flashsale;

import com.ryan.flashsale.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ngày 8: cùng bài test với chiến lược PESSIMISTIC (SELECT FOR UPDATE, Ngày 2)
 * — chứng minh cả hai cách đều đúng; khác nhau ở throughput (đo bằng k6).
 */
@TestPropertySource(properties = "app.reserve-strategy=pessimistic")
class ReservePessimisticStrategyIT extends AbstractIntegrationTest {

    private static final int THREADS = 200;
    private static final int TICKETS = 100;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    OrderRepository orderRepository;

    @Test
    @DisplayName("Pessimistic: 200 user đồng thời -> đúng 100 x 201, 100 đơn, 0 oversell")
    void concurrentReserve_noOversell() throws Exception {
        rest.postForEntity("/api/debug/reset", null, String.class);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREADS);
        AtomicInteger accepted = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        for (int i = 1; i <= THREADS; i++) {
            final String userId = "it-user-" + i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-User-Id", userId);
                    ResponseEntity<String> res = rest.exchange(
                            "/api/events/1/reserve", HttpMethod.POST,
                            new HttpEntity<>(headers), String.class);
                    if (res.getStatusCode().value() == 201) accepted.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneGate.countDown();
                }
            });
        }
        startGate.countDown();
        assertThat(doneGate.await(120, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(accepted.get()).isEqualTo(TICKETS);
        // Sync path: đơn đã nằm trong DB ngay, không cần chờ
        assertThat(orderRepository.count()).isEqualTo(TICKETS);
    }
}
