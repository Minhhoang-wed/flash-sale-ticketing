package com.ryan.flashsale.config;

import com.ryan.flashsale.web.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Chỉ chặn spam ở endpoint nóng nhất
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/events/*/reserve");
    }
}
