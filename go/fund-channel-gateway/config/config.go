package config

import (
	"fmt"
	"strings"
)

// Config is the full gateway runtime configuration loaded from Nacos
// (preferred) or local YAML (dev/test fallback). Field naming aligns
// with the Nacos DataId schema documented in docs/nacos-config.md.
type Config struct {
	Env               string              `yaml:"env"`
	HTTP              HTTPConfig          `yaml:"http"`
	InternalSign      InternalSignConf    `yaml:"internalSign"`
	DefaultProviderID string              `yaml:"defaultProviderId"`
	Providers         map[string]Provider `yaml:"providers"`
	Redis             RedisConfig         `yaml:"redis"`
	RocketMQ          RocketMQConfig      `yaml:"rocketmq"`
	Audit             AuditConfig         `yaml:"audit"`
}

type HTTPConfig struct {
	Addr              string `yaml:"addr"`
	ReadTimeoutMs     int    `yaml:"readTimeoutMs"`
	WriteTimeoutMs    int    `yaml:"writeTimeoutMs"`
	ExposeMockTrigger bool   `yaml:"exposeMockTrigger"`
}

type InternalSignConf struct {
	HeaderName     string `yaml:"headerName"`
	TimestampSkewS int    `yaml:"timestampSkewSeconds"`
	SharedSecret   string `yaml:"sharedSecret"`
	Disabled       bool   `yaml:"disabled"`
}

// Provider holds per-fund-provider connection + crypto + resilience knobs.
// Sensitive values (AppSecret/keys) should be injected by Nacos with strict
// ACL; never commit production values to git.
type Provider struct {
	Enabled          bool          `yaml:"enabled"`
	DisplayName      string        `yaml:"displayName"`
	BaseURL          string        `yaml:"baseUrl"`
	AppKey           string        `yaml:"appKey"`
	AppSecret        string        `yaml:"appSecret"`
	SignAlgorithm    string        `yaml:"signAlgorithm"`
	EncryptFields    []string      `yaml:"encryptFields"`
	AESKey           string        `yaml:"aesKey"`
	RSAPublicKey     string        `yaml:"rsaPublicKey"`
	RSAPrivateKey    string        `yaml:"rsaPrivateKey"`
	HTTPTimeoutMs    int           `yaml:"httpTimeoutMs"`
	MaxRetries       int           `yaml:"maxRetries"`
	RetryBackoffMs   int           `yaml:"retryBackoffMs"`
	CircuitBreaker   CircuitConfig `yaml:"circuitBreaker"`
	CallbackPaths    []string      `yaml:"callbackPaths"`
	CallbackIPAllow  []string      `yaml:"callbackIpAllow"`
	UseMock          bool          `yaml:"useMock"`
	MockAsyncDelayMs int           `yaml:"mockAsyncDelayMs"`
}

type CircuitConfig struct {
	ErrorRatioThreshold float64 `yaml:"errorRatioThreshold"`
	MinRequestAmount    uint64  `yaml:"minRequestAmount"`
	StatIntervalMs      uint32  `yaml:"statIntervalMs"`
	RetryTimeoutMs      uint32  `yaml:"retryTimeoutMs"`
}

type RedisConfig struct {
	Addr     string `yaml:"addr"`
	Password string `yaml:"password"`
	DB       int    `yaml:"db"`
	// IdempotencyTTL governs how long the gateway remembers a (providerId, businessKey, op)
	// fingerprint to short-circuit duplicate receipts and duplicate callbacks.
	IdempotencyTTLSeconds int `yaml:"idempotencyTtlSeconds"`
}

type RocketMQConfig struct {
	NameServer string `yaml:"nameServer"`
	GroupID    string `yaml:"groupId"`
	// Disbursement / Repayment bridge topics. Keep aligned with Java consumers.
	DisburseTopic string `yaml:"disburseTopic"`
	RepayTopic    string `yaml:"repayTopic"`
}

type AuditConfig struct {
	LogPayloadDigest bool `yaml:"logPayloadDigest"`
}

// Normalise applies safe defaults so downstream code can rely on non-zero values
// without sprinkling sentinel checks. Caller MUST still run Validate afterwards.
func (c *Config) Normalise() {
	c.Env = strings.ToLower(strings.TrimSpace(c.Env))
	if c.Env == "" {
		c.Env = "dev"
	}
	if c.HTTP.Addr == "" {
		c.HTTP.Addr = ":8090"
	}
	if c.HTTP.ReadTimeoutMs == 0 {
		c.HTTP.ReadTimeoutMs = 10_000
	}
	if c.HTTP.WriteTimeoutMs == 0 {
		c.HTTP.WriteTimeoutMs = 10_000
	}
	if c.InternalSign.HeaderName == "" {
		c.InternalSign.HeaderName = "X-Internal-Sign"
	}
	if c.InternalSign.TimestampSkewS == 0 {
		c.InternalSign.TimestampSkewS = 300
	}
	if c.Redis.IdempotencyTTLSeconds == 0 {
		c.Redis.IdempotencyTTLSeconds = 86_400 // 24h
	}
	if c.RocketMQ.DisburseTopic == "" {
		c.RocketMQ.DisburseTopic = "FUND_DISBURSED_EVENT"
	}
	if c.RocketMQ.RepayTopic == "" {
		c.RocketMQ.RepayTopic = "REPAYMENT_SETTLED_EVENT"
	}
	if c.RocketMQ.GroupID == "" {
		c.RocketMQ.GroupID = "fund-channel-gateway"
	}
	for id, p := range c.Providers {
		if p.HTTPTimeoutMs == 0 {
			p.HTTPTimeoutMs = 5_000
		}
		if p.RetryBackoffMs == 0 {
			p.RetryBackoffMs = 500
		}
		if p.CircuitBreaker.StatIntervalMs == 0 {
			p.CircuitBreaker.StatIntervalMs = 10_000
		}
		if p.CircuitBreaker.MinRequestAmount == 0 {
			p.CircuitBreaker.MinRequestAmount = 5
		}
		if p.CircuitBreaker.RetryTimeoutMs == 0 {
			p.CircuitBreaker.RetryTimeoutMs = 30_000
		}
		if p.CircuitBreaker.ErrorRatioThreshold == 0 {
			p.CircuitBreaker.ErrorRatioThreshold = 0.5
		}
		c.Providers[id] = p
	}
}

// IsProduction reports whether the active environment must run in
// the strict, no-mock mode. Production gating relies on env, not feature flags.
func (c *Config) IsProduction() bool {
	switch c.Env {
	case "prod", "production":
		return true
	}
	return false
}

// Validate enforces the security and completeness invariants required by spec.
// It MUST be called once at startup; failure MUST abort the process.
func Validate(c *Config) error {
	if c == nil {
		return fmt.Errorf("config is nil")
	}
	if c.DefaultProviderID == "" {
		return fmt.Errorf("defaultProviderId is required (decision #2 hybrid routing)")
	}
	if _, ok := c.Providers[c.DefaultProviderID]; !ok {
		return fmt.Errorf("defaultProviderId %q has no matching provider entry", c.DefaultProviderID)
	}
	if !c.InternalSign.Disabled && c.InternalSign.SharedSecret == "" {
		return fmt.Errorf("internalSign.sharedSecret must be set unless disabled (dev only)")
	}
	if c.IsProduction() && c.InternalSign.Disabled {
		return fmt.Errorf("internalSign cannot be disabled in production (env=%s)", c.Env)
	}
	if len(c.Providers) == 0 {
		return fmt.Errorf("at least one provider entry is required")
	}
	for id, p := range c.Providers {
		if !p.Enabled {
			continue
		}
		if c.IsProduction() && p.UseMock {
			return fmt.Errorf("provider %q has useMock=true in production environment (forbidden)", id)
		}
		if p.UseMock {
			continue // dev/test mock skips secret checks
		}
		if p.BaseURL == "" {
			return fmt.Errorf("provider %q: baseUrl is required", id)
		}
		if p.AppKey == "" || p.AppSecret == "" {
			return fmt.Errorf("provider %q: appKey/appSecret required when useMock=false", id)
		}
		if p.SignAlgorithm == "" {
			return fmt.Errorf("provider %q: signAlgorithm must be specified", id)
		}
	}
	return nil
}
