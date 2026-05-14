package config

import (
	"os"
	"strconv"
)

type JobConfig struct {
	DeductCron          string
	DeductEnabled       bool
	OverdueCron         string
	OverdueEnabled      bool
	PenaltyCron         string
	PenaltyEnabled      bool
	ReminderCron        string
	ReminderEnabled     bool
	RiskDispatchCron    string
	RiskDispatchEnabled bool
	NotificationCron    string
	NotificationEnabled bool
}

type Config struct {
	// Java 微服务地址
	RepaymentServiceURL string
	PostLoanServiceURL  string
	UserServiceURL      string
	// Python Agent 地址
	DataAgentURL string
	// Go fund-channel-gateway 地址；定时代扣 MUST 经由该网关受理。
	FundGatewayURL string
	// 与 Go 网关 internalSign.sharedSecret 对齐的内网签名密钥；生产由运维注入。
	FundGatewaySecret string
	// 业务侧默认资金方 ID；空字符串则由网关回落到 Nacos defaultProviderId。
	FundGatewayDefaultProviderID string
	// 每批代扣并发度（goroutine 数量），单批最大处理量
	DeductConcurrency int
	DeductMaxBatch    int
	// Redis 配置
	RedisAddr     string
	RedisPassword string
	RedisDB       int
	// 管理接口端口
	AdminPort string
	// 任务配置
	Jobs JobConfig
}

func LoadConfig() *Config {
	return &Config{
		RepaymentServiceURL:          getEnv("REPAYMENT_SERVICE_URL", "http://repayment-service:8085"),
		PostLoanServiceURL:           getEnv("POST_LOAN_SERVICE_URL", "http://post-loan-service:8086"),
		UserServiceURL:               getEnv("USER_SERVICE_URL", "http://user-service:8080"),
		DataAgentURL:                 getEnv("DATA_AGENT_URL", "http://data-agent:8000"),
		FundGatewayURL:               getEnv("FUND_CHANNEL_GATEWAY_URL", "http://fund-channel-gateway:8090"),
		FundGatewaySecret:            getEnv("FUND_GATEWAY_SECRET", "default-secret-key-123"),
		FundGatewayDefaultProviderID: getEnv("FUND_GATEWAY_DEFAULT_PROVIDER_ID", ""),
		DeductConcurrency:            getIntEnv("DEDUCT_CONCURRENCY", 8),
		DeductMaxBatch:               getIntEnv("DEDUCT_MAX_BATCH", 500),
		RedisAddr:                    getEnv("REDIS_ADDR", "redis:6379"),
		RedisPassword:                getEnv("REDIS_PASSWORD", ""),
		RedisDB:                      0,
		AdminPort:                    getEnv("ADMIN_PORT", "9090"),
		Jobs: JobConfig{
			DeductCron:          getEnv("JOB_DEDUCT_CRON", "0 0 2 * * *"),
			DeductEnabled:       getEnv("JOB_DEDUCT_ENABLED", "true") == "true",
			OverdueCron:         getEnv("JOB_OVERDUE_CRON", "0 30 2 * * *"),
			OverdueEnabled:      getEnv("JOB_OVERDUE_ENABLED", "true") == "true",
			PenaltyCron:         getEnv("JOB_PENALTY_CRON", "0 0 3 * * *"),
			PenaltyEnabled:      getEnv("JOB_PENALTY_ENABLED", "true") == "true",
			ReminderCron:        getEnv("JOB_REMINDER_CRON", "0 0 9 * * *"),
			ReminderEnabled:     getEnv("JOB_REMINDER_ENABLED", "true") == "true",
			RiskDispatchCron:    getEnv("JOB_RISK_DISPATCH_CRON", "0 0 4 * * *"),
			RiskDispatchEnabled: getEnv("JOB_RISK_DISPATCH_ENABLED", "true") == "true",
			NotificationCron:    getEnv("JOB_NOTIFICATION_CRON", "0 */30 * * * *"),
			NotificationEnabled: getEnv("JOB_NOTIFICATION_ENABLED", "true") == "true",
		},
	}
}

func getEnv(key, fallback string) string {
	if val, ok := os.LookupEnv(key); ok {
		return val
	}
	return fallback
}

func getIntEnv(key string, fallback int) int {
	if val, ok := os.LookupEnv(key); ok {
		if n, err := strconv.Atoi(val); err == nil {
			return n
		}
	}
	return fallback
}
