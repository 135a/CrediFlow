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
// RunNotificationJob 运行通知任务，负责批量推送通知给用户
// 参数:
//
//	cfg: 配置信息，包含用户服务URL等配置
func RunNotificationJob(cfg *config.Config) {
	// 生成基于当前时间的分布式锁键，格式为"lock:batch:notification:日期:时间"
	lockKey := fmt.Sprintf("lock:batch:notification:%s:%s",
		time.Now().Format("20060102"), time.Now().Format("1504"))

	// 尝试获取分布式锁，如果获取失败则跳过本次任务执行
	if !lock.Acquire(lockKey, 10*time.Minute) {
		// 记录任务被跳过的状态
		reporter.Report(reporter.JobResult{
			JobName: "NotificationJob", Status: "SKIPPED", StartTime: time.Now(),
			Detail: "Lock held by another instance",
		})
		return
	}
	// 使用defer确保锁一定会被释放
	defer lock.Release(lockKey)

	// 使用reporter包装任务执行，便于监控和报告
	reporter.RunWithReport("NotificationJob", func() error {
		// 构建通知API的URL
		url := fmt.Sprintf("%s/api/internal/user/notify/batch-push", cfg.UserServiceURL)

		// 构建请求体，包含批处理时间、通知类型和触发源
		body := []byte(fmt.Sprintf(`{"batchTime":"%s","types":["OVERDUE_WARN","SYSTEM_NOTICE"],"triggerSource":"scheduler"}`,
			time.Now().Format("2006-01-02T15:04:05")))
		// 设置请求头，包括内容类型和内部认证令牌
		headers := map[string]string{
			"Content-Type":     "application/json",
			"X-Internal-Token": "batch-scheduler",
		}

		// 带重试机制发送HTTP请求，最多重试2次
		resp, err := httpPostWithRetry(url, body, headers, 2)
		if err != nil {
			return fmt.Errorf("notification push failed: %w", err)
		}
		// 确保响应体被关闭
		defer resp.Body.Close()
		// 记录任务完成状态
		log.Printf("[NotificationJob] Completed. Status: %s\n", resp.Status)
		return nil
	})
}
