package com.ryan.flashsale;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.lifecycle.Startables;

/**
 * Nền cho integration test (Ngày 8): Postgres + Redis + RabbitMQ THẬT
 * chạy trong Docker qua Testcontainers — không mock, không cần cài gì
 * ngoài Docker → chạy được ở mọi máy và trong CI.
 *
 * Container khai báo static + start 1 lần, dùng chung cho mọi test class
 * (mỗi class một Spring context vì properties khác nhau, nhưng hạ tầng chung).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16");

    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:3-management");

    static {
        // deepStart: 3 container kéo lên song song cho nhanh
        Startables.deepStart(POSTGRES, REDIS, RABBIT).join();
    }

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        // 200 user khác nhau, mỗi user 1 request -> nới rate limit cho test ổn định
        registry.add("app.rate-limit-per-second", () -> 1000);
    }
}
