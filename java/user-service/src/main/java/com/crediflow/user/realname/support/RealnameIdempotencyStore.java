package com.crediflow.user.realname.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RealnameIdempotencyStore {

    private final StringRedisTemplate redis;

    public RealnameIdempotencyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    public void put(String key, String json, Duration ttl) {
        redis.opsForValue().set(key, json, ttl);
    }
}
