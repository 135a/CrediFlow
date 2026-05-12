package jobs

import (
	"crediflow/batch-service/config"
	"crediflow/batch-service/lock"
	"crediflow/batch-service/reporter"
	"fmt"
	"log"
	"time"
)

// RunNotificationJob 消息推送调度任务
// 每 30 分钟执行一次，统一调度待发送的用户通知（逾期提醒、系统公告、活动推送等）
func RunNotificationJob(cfg *config.Config) {
	lockKey := fmt.Sprintf("lock:batch:notification:%s:%s",
		time.Now().Format("20060102"), time.Now().Format("1504"))

	if !lock.Acquire(lockKey, 10*time.Minute) {
		reporter.Report(reporter.JobResult{
			JobName: "NotificationJob", Status: "SKIPPED", StartTime: time.Now(),
			Detail: "Lock held by another instance",
		})
		return
	}
	defer lock.Release(lockKey)

	reporter.RunWithReport("NotificationJob", func() error {
		url := fmt.Sprintf("%s/api/internal/user/notify/batch-push", cfg.UserServiceURL)

		body := []byte(fmt.Sprintf(`{"batchTime":"%s","types":["OVERDUE_WARN","SYSTEM_NOTICE"],"triggerSource":"scheduler"}`,
			time.Now().Format("2006-01-02T15:04:05")))
		headers := map[string]string{
			"Content-Type":     "application/json",
			"X-Internal-Token": "batch-scheduler",
		}

		resp, err := httpPostWithRetry(url, body, headers, 2)
		if err != nil {
			return fmt.Errorf("notification push failed: %w", err)
		}
		defer resp.Body.Close()
		log.Printf("[NotificationJob] Completed. Status: %s\n", resp.Status)
		return nil
	})
}
