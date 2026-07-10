package com.ryan.flashsale.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "reservation.exchange";
    public static final String QUEUE = "reservation.queue";
    public static final String ROUTING_KEY = "reservation.created";

    @Bean
    public DirectExchange reservationExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Queue reservationQueue() {
        // durable = true: queue sống sót khi RabbitMQ restart
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding reservationBinding() {
        return BindingBuilder.bind(reservationQueue())
                .to(reservationExchange())
                .with(ROUTING_KEY);
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
