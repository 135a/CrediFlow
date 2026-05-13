package jobs

import (
	"crediflow/batch-service/config"
	"crediflow/batch-service/lock"
	"crediflow/batch-service/reporter"
	"fmt"
	"log"
	"time"
)

// RunPenaltyJob 罚息计算任务
// 每日凌晨 3:00 执行，调用 post-loan-service 对所有逾期订单进行罚息累计计算
func RunPenaltyJob(cfg *config.Config) {
	lockKey := fmt.Sprintf("lock:batch:penalty:%s", time.Now().Format("20060102"))

	if !lock.Acquire(lockKey, 20*time.Minute) {
		reporter.Report(reporter.JobResult{
			JobName: "PenaltyJob", Status: "SKIPPED", StartTime: time.Now(),
			Detail: "Lock held by another instance",
		})
		return
	}
	defer lock.Release(lockKey)

	reporter.RunWithReport("PenaltyJob", func() error {
		url := fmt.Sprintf("%s/api/internal/post-loan/penalty/calculate", cfg.PostLoanServiceURL)

		body := []byte(fmt.Sprintf(`{"calcDate":"%s","triggerSource":"scheduler"}`, time.Now().Format("2006-01-02")))
		headers := map[string]string{
			"Content-Type":     "application/json",
			"X-Internal-Token": "batch-scheduler",
		}

		resp, err := httpPostWithRetry(url, body, headers, 3)
		if err != nil {
			return fmt.Errorf("penalty calculation failed: %w", err)
		}
		defer resp.Body.Close()
		log.Printf("[PenaltyJob] Completed. Status: %s\n", resp.Status)
		return nil
	})
}
