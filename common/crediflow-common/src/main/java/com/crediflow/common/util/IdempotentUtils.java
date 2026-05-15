package com.crediflow.common.util;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class IdempotentUtils {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 尝试获取锁，用于幂等控制
     *
     * @param key        锁的key
     * @param waitTime   等待时间
     * @param leaseTime  锁持有的超时时间
     * @param timeUnit   时间单位
     * @return 是否获取成功
     */
    public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit timeUnit) {
        RLock lock = redissonClient.getLock("IDMP:LOCK:" + key);
        try {
            return lock.tryLock(waitTime, leaseTime, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放锁
     *
     * @param key 锁的key
     */
    public void unlock(String key) {
        RLock lock = redissonClient.getLock("IDMP:LOCK:" + key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
