package com.ryan.flashsale.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.time.Instant;

/**
 * Rate limit theo user (Ngày 6): INCR req:{userId}:{giây} + EXPIRE 1.
 * Quá N request/giây → 429 Too Many Requests.
 *
 * Cửa sổ trượt theo giây (fixed window 1s) — đơn giản, đủ chặn spam nút F5.
 * Lưu ý đã ghi trong NOTES: INCR + EXPIRE là 2 lệnh, về lý thuyết cũng có
 * khe hở (crash giữa chừng → key không TTL). Production sẽ gộp bằng Lua —
 * đúng bài học của ngày hôm nay.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redis;

    @Value("${app.rate-limit-per-second:5}")
    private int maxRequestsPerSecond;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            return true; // controller sẽ trả 400 vì thiếu header
        }
        String key = "req:" + userId + ":" + Instant.now().getEpochSecond();
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofSeconds(1));
        }
        if (count != null && count > maxRequestsPerSecond) {
            log.info("Rate limited user {}: {} req/s (max {})", userId, count, maxRequestsPerSecond);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Max " + maxRequestsPerSecond + " requests/second\"}");
            return false;
        }
        return true;
    }
}
