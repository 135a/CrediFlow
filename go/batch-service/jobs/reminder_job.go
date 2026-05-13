package jobs

import (
	"crediflow/batch-service/config"
	"crediflow/batch-service/lock"
	"crediflow/batch-service/reporter"
	"fmt"
	"log"
	"time"
)

// RunReminderJob 到期还款提醒任务
// 每日上午 9:00 执行，调用 user-service 向即将到期（T-3 天）的用户推送还款提醒
func RunReminderJob(cfg *config.Config) {
	lockKey := fmt.Sprintf("lock:batch:reminder:%s", time.Now().Format("20060102"))

	if !lock.Acquire(lockKey, 15*time.Minute) {
		reporter.Report(reporter.JobResult{
			JobName: "ReminderJob", Status: "SKIPPED", StartTime: time.Now(),
			Detail: "Lock held by another instance",
		})
		return
	}
	defer lock.Release(lockKey)

	reporter.RunWithReport("ReminderJob", func() error {
		url := fmt.Sprintf("%s/api/internal/user/notify/repayment-reminder", cfg.UserServiceURL)

		dueDate := time.Now().AddDate(0, 0, 3).Format("2006-01-02")
		body := []byte(fmt.Sprintf(`{"dueDate":"%s","reminderType":"PRE_DUE","triggerSource":"scheduler"}`, dueDate))
		headers := map[string]string{
			"Content-Type":     "application/json",
			"X-Internal-Token": "batch-scheduler",
		}

		resp, err := httpPostWithRetry(url, body, headers, 2)
		if err != nil {
			return fmt.Errorf("reminder notification failed: %w", err)
		}
		defer resp.Body.Close()
		log.Printf("[ReminderJob] Completed. Status: %s, dueDate: %s\n", resp.Status, dueDate)
		return nil
	})
}
