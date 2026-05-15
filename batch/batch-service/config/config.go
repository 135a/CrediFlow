package config

import (
	"os"
	"strconv"
)

type JobConfig struct {
	// DeductCron 表示扣除金额的定时任务表达式
	DeductCron string
	// DeductEnabled 表示是否启用扣除金额的定时任务
	DeductEnabled bool
	// OverdueCron 表示逾期处理的定时任务表达式
	OverdueCron string
	// OverdueEnabled 表示是否启用逾期处理的定时任务
	OverdueEnabled bool
	// PenaltyCron 表示罚金的定时任务表达式
	PenaltyCron string
	// PenaltyEnabled 表示是否启用罚金的定时任务
	PenaltyEnabled bool
	// ReminderCron 表示提醒的定时任务表达式
	ReminderCron string
	// ReminderEnabled 表示是否启用提醒的定时任务
	ReminderEnabled bool
	// RiskDispatchCron 表示风险调度的定时任务表达式
	RiskDispatchCron string
	// RiskDispatchEnabled 表示是否启用风险调度的定时任务
	RiskDispatchEnabled bool
	// NotificationCron 表示通知的定时任务表达式
	NotificationCron string
	// NotificationEnabled 表示是否启用通知的定时任务
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

// LoadConfig 加载并配置应用程序所需的各种参数
// 该函数返回一个Config结构体指针，其中包含了系统运行所需的各种配置信息
// 包括服务URL、密钥、并发设置、Redis配置以及各种定时任务的配置
func LoadConfig() *Config {
	return &Config{

		// 还款服务相关配置
		RepaymentServiceURL:          getEnv("REPAYMENT_SERVICE_URL", "http://repayment-service:8085"),       // 还款服务地址
		PostLoanServiceURL:           getEnv("POST_LOAN_SERVICE_URL", "http://post-loan-service:8086"),       // 放款后服务地址
		UserServiceURL:               getEnv("USER_SERVICE_URL", "http://user-service:8080"),                 // 用户服务地址
		DataAgentURL:                 getEnv("DATA_AGENT_URL", "http://data-agent:8000"),                     // 数据代理服务地址
		FundGatewayURL:               getEnv("FUND_CHANNEL_GATEWAY_URL", "http://fund-channel-gateway:8090"), // 资金网关地址
		FundGatewaySecret:            getEnv("FUND_GATEWAY_SECRET", "default-secret-key-123"),                // 资金网关密钥
		FundGatewayDefaultProviderID: getEnv("FUND_GATEWAY_DEFAULT_PROVIDER_ID", ""),                         // 资金网关默认提供商ID

		// 系统并发控制配置
		DeductConcurrency: getIntEnv("DEDUCT_CONCURRENCY", 8), // 扣款并发数
		DeductMaxBatch:    getIntEnv("DEDUCT_MAX_BATCH", 500), // 扣款最大批处理量

		// Redis相关配置
		RedisAddr:     getEnv("REDIS_ADDR", "redis:6379"), // Redis服务器地址
		RedisPassword: getEnv("REDIS_PASSWORD", ""),       // Redis密码
		RedisDB:       0,                                  // Redis数据库编号

		// 管理端口配置
		AdminPort: getEnv("ADMIN_PORT", "9090"), // 管理端口
		// 定时任务配置
		Jobs: JobConfig{
			DeductCron:          getEnv("JOB_DEDUCT_CRON", "0 0 2 * * *"),              // 扣款任务定时配置
			DeductEnabled:       getEnv("JOB_DEDUCT_ENABLED", "true") == "true",        // 是否启用扣款任务
			OverdueCron:         getEnv("JOB_OVERDUE_CRON", "0 30 2 * * *"),            // 逾期任务定时配置
			OverdueEnabled:      getEnv("JOB_OVERDUE_ENABLED", "true") == "true",       // 是否启用逾期任务
			PenaltyCron:         getEnv("JOB_PENALTY_CRON", "0 0 3 * * *"),             // 罚息任务定时配置
			PenaltyEnabled:      getEnv("JOB_PENALTY_ENABLED", "true") == "true",       // 是否启用罚息任务
			ReminderCron:        getEnv("JOB_REMINDER_CRON", "0 0 9 * * *"),            // 提醒任务定时配置
			ReminderEnabled:     getEnv("JOB_REMINDER_ENABLED", "true") == "true",      // 是否启用提醒任务
			RiskDispatchCron:    getEnv("JOB_RISK_DISPATCH_CRON", "0 0 4 * * *"),       // 风险调度任务定时配置
			RiskDispatchEnabled: getEnv("JOB_RISK_DISPATCH_ENABLED", "true") == "true", // 是否启用风险调度任务
			NotificationCron:    getEnv("JOB_NOTIFICATION_CRON", "0 */30 * * * *"),     // 通知任务定时配置
			NotificationEnabled: getEnv("JOB_NOTIFICATION_ENABLED", "true") == "true",  // 是否启用通知任务
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
