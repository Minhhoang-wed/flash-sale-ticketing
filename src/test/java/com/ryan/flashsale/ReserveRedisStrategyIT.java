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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Ngày 8: test concurrency với chiến lược REDIS (Lua + RabbitMQ async).
 * 200 user đồng thời tranh 100 vé → đúng 100 được nhận (202),
 * và cuối cùng đúng 100 đơn trong DB — 0 oversell.
 */
@TestPropertySource(properties = "app.reserve-strategy=redis")
class ReserveRedisStrategyIT extends AbstractIntegrationTest {

    private static final int THREADS = 200;
    private static final int TICKETS = 100;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    OrderRepository orderRepository;

    @Test
    @DisplayName("200 user đồng thời, 100 vé -> đúng 100 x 202, 100 x 409, 100 đơn trong DB")
    void concurrentReserve_noOversell() throws Exception {
        rest.postForEntity("/api/debug/reset", null, String.class);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(THREADS);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

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
                    if (res.getStatusCode().value() == 202) accepted.incrementAndGet();
                    else rejected.incrementAndGet();
                } catch (Exception ignored) {
                    rejected.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }
        startGate.countDown();
        assertThat(doneGate.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // Đúng 100 giữ chỗ thành công — không hơn (oversell), không kém
        assertThat(accepted.get()).isEqualTo(TICKETS);
        assertThat(rejected.get()).isEqualTo(THREADS - TICKETS);

        // Consumer ghi đơn async -> chờ tối đa 15s cho queue drain
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(orderRepository.count()).isEqualTo(TICKETS));
    }
}
