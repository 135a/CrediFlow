package idempotency

import (
	"context"
	"fmt"
	"sync"
	"time"

	"crediflow/fund-channel-gateway/config"
	"crediflow/fund-channel-gateway/logger"

	"github.com/redis/go-redis/v9"
)

type Store interface {
	// TryClaim records the claim atomically; returns (true, nil) only if this caller
	// owns the key for the configured TTL. Existing owners or transport errors return
	// (false, err) where err may be nil for "already claimed".
	TryClaim(ctx context.Context, key string) (bool, error)
	// Mark force-writes the key (used after we know terminal state). It is idempotent.
	Mark(ctx context.Context, key string, ttl time.Duration) error
	// Exists checks without mutating.
	Exists(ctx context.Context, key string) (bool, error)
}

type redisStore struct {
	rdb *redis.Client
	ttl time.Duration
}

type memoryStore struct {
	mu   sync.Mutex
	keys map[string]time.Time
	ttl  time.Duration
}

var (
	def      Store
	initOnce sync.Once
)

// Init wires up the default store. Falls back to an in-memory store if Redis
// is unreachable, with a loud warning - this is acceptable for dev only.
func Init(cfg config.RedisConfig) {
	initOnce.Do(func() {
		ttl := time.Duration(cfg.IdempotencyTTLSeconds) * time.Second
		if cfg.Addr == "" {
			logger.Warn("Idempotency", "redis.addr empty, using in-memory store (dev only)")
			def = &memoryStore{keys: map[string]time.Time{}, ttl: ttl}
			return
		}
		rdb := redis.NewClient(&redis.Options{
			Addr:     cfg.Addr,
			Password: cfg.Password,
			DB:       cfg.DB,
		})
		ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
		defer cancel()
		if err := rdb.Ping(ctx).Err(); err != nil {
			logger.Warn("Idempotency", "redis ping failed (%v), falling back to in-memory", err)
			def = &memoryStore{keys: map[string]time.Time{}, ttl: ttl}
			return
		}
		def = &redisStore{rdb: rdb, ttl: ttl}
		logger.Info("Idempotency", "redis store initialised addr=%s ttl=%s", cfg.Addr, ttl)
	})
}

func Default() Store { return def }

// Key builds the canonical key. Aligning to (providerId, businessKey, operation)
// satisfies the spec invariant against duplicate disbursement & duplicate withholding.
func Key(providerID, businessKey, operation string) string {
	return fmt.Sprintf("fund:gw:idmp:%s:%s:%s", providerID, operation, businessKey)
}

// CallbackKey is used to short-circuit duplicate provider callbacks.
func CallbackKey(providerID, providerTxnNo string) string {
	return fmt.Sprintf("fund:gw:cb:%s:%s", providerID, providerTxnNo)
}

func (s *redisStore) TryClaim(ctx context.Context, key string) (bool, error) {
	return s.rdb.SetNX(ctx, key, "1", s.ttl).Result()
}

func (s *redisStore) Mark(ctx context.Context, key string, ttl time.Duration) error {
	if ttl <= 0 {
		ttl = s.ttl
	}
	return s.rdb.Set(ctx, key, "1", ttl).Err()
}

func (s *redisStore) Exists(ctx context.Context, key string) (bool, error) {
	n, err := s.rdb.Exists(ctx, key).Result()
	return n > 0, err
}

func (m *memoryStore) TryClaim(_ context.Context, key string) (bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	now := time.Now()
	if exp, ok := m.keys[key]; ok && exp.After(now) {
		return false, nil
	}
	m.keys[key] = now.Add(m.ttl)
	return true, nil
}

func (m *memoryStore) Mark(_ context.Context, key string, ttl time.Duration) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if ttl <= 0 {
		ttl = m.ttl
	}
	m.keys[key] = time.Now().Add(ttl)
	return nil
}

func (m *memoryStore) Exists(_ context.Context, key string) (bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	exp, ok := m.keys[key]
	if !ok {
		return false, nil
	}
	return exp.After(time.Now()), nil
}
