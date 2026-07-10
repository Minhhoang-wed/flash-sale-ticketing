package com.ryan.flashsale.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisConfig {

    /**
     * Lua script chạy trong Redis như MỘT khối atomic (Ngày 6):
     * check đã-mua + check stock + DECR + SADD — không request nào chen ngang được.
     */
    @Bean
    public DefaultRedisScript<Long> reserveScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/reserve.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
