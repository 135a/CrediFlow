package com.crediflow.repayment.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * Redis 分布式锁（任务 3.3；后续可上移至 crediflow-common 以免无 Redis 时拖垮全部服务）。
 */
public class RedisDistributedLock {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>();
    static {
        UNLOCK_SCRIPT.setResultType(Long.class);
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
    }

    private final StringRedisTemplate redis;

    public RedisDistributedLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        redis.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
    }
}
