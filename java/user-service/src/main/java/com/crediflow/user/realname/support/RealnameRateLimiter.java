package com.crediflow.user.realname.support;

import com.crediflow.user.realname.config.RealnameProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RealnameRateLimiter {

    private final StringRedisTemplate redis;
    private final RealnameProperties properties;

    public RealnameRateLimiter(StringRedisTemplate redis, RealnameProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public boolean tryAcquire(long userId) {
        int window = Math.max(1, properties.getRateLimitWindowSeconds());
        long bucket = Instant.now().getEpochSecond() / window;
        String key = "realname:rl:" + userId + ":" + bucket;
        Long c = redis.opsForValue().increment(key);
        if (c != null && c == 1) {
            redis.expire(key, Duration.ofSeconds(window));
        }
        int max = Math.max(1, properties.getRateLimitMaxRequests());
        return c != null && c <= max;
    }
}
