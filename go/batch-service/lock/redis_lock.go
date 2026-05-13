package lock

import (
	"context"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/redis/go-redis/v9"
)

var (
	rdb        *redis.Client
	instanceID string
)

// Init 初始化 Redis 客户端
func Init(addr, password string, db int) {
	rdb = redis.NewClient(&redis.Options{
		Addr:     addr,
		Password: password,
		DB:       db,
	})

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := rdb.Ping(ctx).Err(); err != nil {
		log.Printf("[RedisLock] Warning: Redis connection failed: %v (lock will be disabled)\n", err)
		rdb = nil
		return
	}

	// 使用 hostname + pid 作为实例唯一标识，防止误删他人锁
	hostname, _ := os.Hostname()
	instanceID = fmt.Sprintf("%s-%d", hostname, os.Getpid())
	log.Printf("[RedisLock] Connected to Redis at %s, instanceID=%s\n", addr, instanceID)
}

// Acquire 尝试获取分布式锁
// key: 锁名称（如 "lock:deduct:20260512"）
// ttl: 锁持有时间上限，防止死锁
// 返回 true 表示获取成功，false 表示已被其他实例持有
func Acquire(key string, ttl time.Duration) bool {
	if rdb == nil {
		log.Println("[RedisLock] Redis not available, proceeding without lock (single instance mode)")
		return true
	}

	ctx := context.Background()
	ok, err := rdb.SetNX(ctx, key, instanceID, ttl).Result()
	if err != nil {
		log.Printf("[RedisLock] Error acquiring lock %s: %v\n", key, err)
		return false
	}

	if ok {
		log.Printf("[RedisLock] Acquired lock: %s (ttl=%v)\n", key, ttl)
	} else {
		holder, _ := rdb.Get(ctx, key).Result()
		log.Printf("[RedisLock] Lock %s already held by %s, skipping.\n", key, holder)
	}
	return ok
}

// Release 释放分布式锁（仅释放自己持有的锁，防止误删）
func Release(key string) {
	if rdb == nil {
		return
	}

	ctx := context.Background()
	// Lua 脚本保证原子性：只有锁的持有者才能删除
	script := redis.NewScript(`
		if redis.call("get", KEYS[1]) == ARGV[1] then
			return redis.call("del", KEYS[1])
		else
			return 0
		end
	`)

	result, err := script.Run(ctx, rdb, []string{key}, instanceID).Int64()
	if err != nil {
		log.Printf("[RedisLock] Error releasing lock %s: %v\n", key, err)
		return
	}

	if result == 1 {
		log.Printf("[RedisLock] Released lock: %s\n", key)
	} else {
		log.Printf("[RedisLock] Lock %s was not held by this instance, skip release.\n", key)
	}
}
