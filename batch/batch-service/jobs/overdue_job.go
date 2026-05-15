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
// RunOverdueJob 执行逾期任务函数
// 该函数用于处理逾期扫描任务，包含分布式锁控制、HTTP请求和错误报告等功能
// 参数:
//   - cfg: 配置信息指针，包含后端服务URL等配置
func RunOverdueJob(cfg *config.Config) {
	// 生成基于当前日期的分布式锁键，格式为"lock:batch:overdue:YYYYMMDD"
	lockKey := fmt.Sprintf("lock:batch:overdue:%s", time.Now().Format("20060102"))

	// 尝试获取分布式锁，如果获取失败则跳过任务执行
	if !lock.Acquire(lockKey, 30*time.Minute) {
		// 记录任务被跳过的状态报告
		reporter.Report(reporter.JobResult{
			JobName: "OverdueJob", Status: "SKIPPED", StartTime: time.Now(),
			Detail: "Lock held by another instance",
		})
		return
	}
	// 使用defer确保锁一定会被释放
	defer lock.Release(lockKey)

	// 执行任务并记录结果
	reporter.RunWithReport("OverdueJob", func() error {
		// 构建逾期扫描API的URL
		url := fmt.Sprintf("%s/api/internal/post-loan/overdue/scan", cfg.PostLoanServiceURL)

		// 构造请求体，包含扫描日期和触发来源
		body := []byte(fmt.Sprintf(`{"scanDate":"%s","triggerSource":"scheduler"}`, time.Now().Format("2006-01-02")))
		// 设置HTTP请求头
		headers := map[string]string{
			"Content-Type":     "application/json",
			"X-Internal-Token": "batch-scheduler",
		}

		// 带重试机制的HTTP POST请求，最多重试3次
		resp, err := httpPostWithRetry(url, body, headers, 3)
		if err != nil {
			return fmt.Errorf("overdue scan call failed: %w", err)
		}
		// 确保响应体被关闭
		defer resp.Body.Close()
		log.Printf("[OverdueJob] Completed. Status: %s\n", resp.Status)
		return nil
	})
}
