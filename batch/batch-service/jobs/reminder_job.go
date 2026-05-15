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
// RunReminderJob 执行还款提醒任务，通过分布式锁确保同一时间只有一个实例在运行
// 参数:
//
//	cfg: 配置信息，包含用户服务URL等必要配置
func RunReminderJob(cfg *config.Config) {
	// 生成基于当前日期的分布式锁键，格式为"lock:batch:reminder:YYYYMMDD"
	lockKey := fmt.Sprintf("lock:batch:reminder:%s", time.Now().Format("20060102"))

	// 尝试获取分布式锁，锁的持续时间为15分钟
	// 如果获取失败，说明有其他实例正在执行此任务
	if !lock.Acquire(lockKey, 15*time.Minute) {
		// 记录任务被跳过的状态
		reporter.Report(reporter.JobResult{
			JobName: "ReminderJob", Status: "SKIPPED", StartTime: time.Now(),
			Detail: "Lock held by another instance",
		})
		return
	}
	// 使用defer确保在函数退出时释放锁
	defer lock.Release(lockKey)

	// 使用reporter.RunWithReport执行任务，并记录执行结果
	reporter.RunWithReport("ReminderJob", func() error {
		// 构建用户通知服务的URL
		url := fmt.Sprintf("%s/api/internal/user/notify/repayment-reminder", cfg.UserServiceURL)

		// 计算3天后的到期日，并格式化为"YYYY-MM-DD"格式
		dueDate := time.Now().AddDate(0, 0, 3).Format("2006-01-02")
		// 构建请求体，包含到期日、提醒类型和触发源
		body := []byte(fmt.Sprintf(`{"dueDate":"%s","reminderType":"PRE_DUE","triggerSource":"scheduler"}`, dueDate))
		// 设置请求头，包括内容类型和内部认证令牌
		headers := map[string]string{
			"Content-Type":     "application/json",
			"X-Internal-Token": "batch-scheduler",
		}

		// 带重试机制发送HTTP请求，最多重试2次
		resp, err := httpPostWithRetry(url, body, headers, 2)
		if err != nil {
			// 如果请求失败，返回包装后的错误信息
			return fmt.Errorf("reminder notification failed: %w", err)
		}
		defer resp.Body.Close()
		log.Printf("[ReminderJob] Completed. Status: %s, dueDate: %s\n", resp.Status, dueDate)
		return nil
	})
}
