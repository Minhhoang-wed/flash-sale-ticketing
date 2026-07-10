package com.ryan.flashsale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlashSaleTicketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashSaleTicketingApplication.class, args);
    }
}
