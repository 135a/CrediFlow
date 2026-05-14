package reporter

import (
	"encoding/json"
	"log"
	"time"
)

// JobResult 任务执行结果的结构化日志
type JobResult struct {
	JobName   string        `json:"job_name"`
	Status    string        `json:"status"` // SUCCESS / FAILED / SKIPPED
	StartTime time.Time     `json:"start_time"`
	Duration  time.Duration `json:"duration_ms"`
	Error     string        `json:"error,omitempty"`
	Detail    string        `json:"detail,omitempty"`
}

// Report 输出结构化 JSON 日志
func Report(result JobResult) {
	result.Duration = result.Duration / time.Millisecond
	data, err := json.Marshal(result)
	if err != nil {
		log.Printf("[Reporter] Failed to marshal result: %v\n", err)
		return
	}
	log.Printf("[JobReport] %s\n", string(data))
}

// RunWithReport 通用包装器：为任意 Job 函数自动记录执行日志
func RunWithReport(jobName string, fn func() error) {
	start := time.Now()
	err := fn()
	duration := time.Since(start)

	result := JobResult{
		JobName:   jobName,
		StartTime: start,
		Duration:  duration,
	}

	if err != nil {
		result.Status = "FAILED"
		result.Error = err.Error()
	} else {
		result.Status = "SUCCESS"
	}

	Report(result)
}
