package com.crediflow.user.realname.support;

import com.crediflow.user.realname.config.RealnameProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealnameRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Test
    void blocksWhenOverLimit() {
        RealnameProperties props = new RealnameProperties();
        props.setRateLimitWindowSeconds(60);
        props.setRateLimitMaxRequests(2);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(3L);

        RealnameRateLimiter limiter = new RealnameRateLimiter(redis, props);
        assertThat(limiter.tryAcquire(100L)).isFalse();
    }

    @Test
    void allowsWithinLimit() {
        RealnameProperties props = new RealnameProperties();
        props.setRateLimitWindowSeconds(60);
        props.setRateLimitMaxRequests(5);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redis.expire(anyString(), any(java.time.Duration.class))).thenReturn(true);

        RealnameRateLimiter limiter = new RealnameRateLimiter(redis, props);
        assertThat(limiter.tryAcquire(200L)).isTrue();
    }
}
