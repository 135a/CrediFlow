package jobs

import (
	"crediflow/batch-service/config"
	"crediflow/batch-service/lock"
	"crediflow/batch-service/reporter"
	"fmt"
	"log"
	"time"
)

// RunRiskDispatchJob 风控异步分发任务
// 每日凌晨 4:00 执行，将高风险用户清单投递给 Python Data Agent 进行离线风控评估
func RunRiskDispatchJob(cfg *config.Config) {
	lockKey := fmt.Sprintf("lock:batch:risk_dispatch:%s", time.Now().Format("20060102"))

	if !lock.Acquire(lockKey, 30*time.Minute) {
		reporter.Report(reporter.JobResult{
			JobName: "RiskDispatchJob", Status: "SKIPPED", StartTime: time.Now(),
			Detail: "Lock held by another instance",
		})
		return
	}
	defer lock.Release(lockKey)

	reporter.RunWithReport("RiskDispatchJob", func() error {
		url := fmt.Sprintf("%s/api/v1/credit/evaluate", cfg.DataAgentURL)

		// 实际场景中应先从 Java 端查询待评估用户列表，逐个或批量投递
		// 这里示范单次调用
		body := []byte(`{"userId":0,"age":0,"income":0,"batchMode":true,"triggerSource":"scheduler"}`)
		headers := map[string]string{
			"Content-Type":     "application/json",
			"X-Internal-Token": "batch-scheduler",
		}

		resp, err := httpPostWithRetry(url, body, headers, 2)
		if err != nil {
			return fmt.Errorf("risk dispatch to agent failed: %w", err)
		}
		defer resp.Body.Close()
		log.Printf("[RiskDispatchJob] Completed. Status: %s\n", resp.Status)
		return nil
	})
}
