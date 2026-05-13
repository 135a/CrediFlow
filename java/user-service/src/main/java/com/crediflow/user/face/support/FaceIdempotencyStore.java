package com.crediflow.user.face.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 幂等与中间态存储。
 *
 * <ul>
 *   <li>{@code face:cb:&lt;providerId&gt;:&lt;providerTxnNo&gt;} — 回调幂等键（24h）</li>
 *   <li>{@code face:state:&lt;userId&gt;:&lt;bizNo&gt;} — 受理后中间态（30min 默认）</li>
 *   <li>{@code face:idem:&lt;userId&gt;:&lt;idempotencyKey&gt;} — 客户端 Idempotency-Key 短路</li>
 * </ul>
 */
@Component
public class FaceIdempotencyStore {

    private final StringRedisTemplate redis;

    public FaceIdempotencyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Try acquire callback idempotency. true 表示首次。 */
    public boolean tryAcquireCallback(String providerId, String providerTxnNo, Duration ttl) {
        if (providerId == null || providerTxnNo == null) {
            return true;
        }
        Boolean ok = redis.opsForValue().setIfAbsent("face:cb:" + providerId + ":" + providerTxnNo, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    public void writeState(long userId, String bizNo, String state, Duration ttl) {
        redis.opsForValue().set("face:state:" + userId + ":" + bizNo, state, ttl);
    }

    public String readState(long userId, String bizNo) {
        return redis.opsForValue().get("face:state:" + userId + ":" + bizNo);
    }

    public boolean tryAcquireClientIdempotency(long userId, String idempotencyKey, Duration ttl) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true;
        }
        Boolean ok = redis.opsForValue().setIfAbsent(
                "face:idem:" + userId + ":" + idempotencyKey.trim(), "1", ttl);
        return Boolean.TRUE.equals(ok);
    }
}
