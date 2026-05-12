package jobs

import (
	"crediflow/batch-service/config"
	"crediflow/batch-service/lock"
	"crediflow/batch-service/reporter"
	"fmt"
	"log"
	"time"
)

// RunOverdueJob 逾期巡检任务（带分布式锁 + 重试）
// 每日凌晨 2:30 执行，扫描所有到期未还的还款计划，标记为逾期状态并触发催收流程
func RunOverdueJob(cfg *config.Config) {
	lockKey := fmt.Sprintf("lock:batch:overdue:%s", time.Now().Format("20060102"))

	if !lock.Acquire(lockKey, 30*time.Minute) {
		reporter.Report(reporter.JobResult{
			JobName: "OverdueJob", Status: "SKIPPED", StartTime: time.Now(),
			Detail: "Lock held by another instance",
		})
		return
	}
	defer lock.Release(lockKey)

	reporter.RunWithReport("OverdueJob", func() error {
		url := fmt.Sprintf("%s/api/internal/post-loan/overdue/scan", cfg.PostLoanServiceURL)

		body := []byte(fmt.Sprintf(`{"scanDate":"%s","triggerSource":"scheduler"}`, time.Now().Format("2006-01-02")))
		headers := map[string]string{
			"Content-Type":     "application/json",
			"X-Internal-Token": "batch-scheduler",
		}

		resp, err := httpPostWithRetry(url, body, headers, 3)
		if err != nil {
			return fmt.Errorf("overdue scan call failed: %w", err)
		}
		defer resp.Body.Close()
		log.Printf("[OverdueJob] Completed. Status: %s\n", resp.Status)
		return nil
	})
}
