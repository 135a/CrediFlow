package config

import "os"

type JobConfig struct {
	DeductCron     string
	DeductEnabled  bool
	OverdueCron    string
	OverdueEnabled bool
}

type Config struct {
	RepaymentServiceURL string
	PostLoanServiceURL  string
	Jobs                JobConfig
}

func LoadConfig() *Config {
	return &Config{
		RepaymentServiceURL: getEnv("REPAYMENT_SERVICE_URL", "http://localhost:8080"),
		PostLoanServiceURL:  getEnv("POST_LOAN_SERVICE_URL", "http://localhost:8080"),
		Jobs: JobConfig{
			DeductCron:     getEnv("JOB_DEDUCT_CRON", "0 0 2 * * *"), // 每天凌晨 2 点
			DeductEnabled:  getEnv("JOB_DEDUCT_ENABLED", "true") == "true",
			OverdueCron:    getEnv("JOB_OVERDUE_CRON", "0 30 2 * * *"), // 每天凌晨 2 点 30
			OverdueEnabled: getEnv("JOB_OVERDUE_ENABLED", "true") == "true",
		},
	}
}

func getEnv(key, fallback string) string {
	if val, ok := os.LookupEnv(key); ok {
		return val
	}
	return fallback
}
