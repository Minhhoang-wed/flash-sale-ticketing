package com.ryan.flashsale.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "reservation.exchange";
    public static final String QUEUE = "reservation.queue";
    public static final String ROUTING_KEY = "reservation.created";

    // Ngày 7: Dead Letter — nơi hạ cánh của message fail sau khi hết retry
    public static final String DLX = "reservation.dlx";
    public static final String DLQ = "reservation.dlq";
    public static final String DLQ_ROUTING_KEY = "reservation.dead";

    @Bean
    public DirectExchange reservationExchange() {
        return new DirectExchange(EXCHANGE);
    }

    /**
     * Queue chính gắn dead-letter-exchange (Ngày 7):
     * khi consumer reject không requeue (hết 3 lần retry),
     * broker tự chuyển message sang DLX → DLQ.
     *
     * LƯU Ý: đổi args của queue đã tồn tại sẽ bị PRECONDITION_FAILED —
     * phải xóa container rabbitmq cũ (docker compose down) trước khi up.
     */
    @Bean
    public Queue reservationQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding reservationBinding() {
        return BindingBuilder.bind(reservationQueue())
                .to(reservationExchange())
                .with(ROUTING_KEY);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX);
    }

    @Bean
    public Queue reservationDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(reservationDlq())
                .to(deadLetterExchange())
                .with(DLQ_ROUTING_KEY);
    }

    /**
     * Message serialize bằng JSON thay vì Java serialization.
     * Dùng ObjectMapper của Spring (đã có module java.time cho Instant).
     * Boot tự gắn converter này vào cả RabbitTemplate lẫn @RabbitListener.
     */
    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
