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
// RunPenaltyJob 执行罚金计算任务的主函数
// 参数:
//
//	cfg: 配置信息指针，包含服务URL等配置
func RunPenaltyJob(cfg *config.Config) {
	// 生成基于当前日期的分布式锁键，格式为 "lock:batch:penalty:YYYYMMDD"
	lockKey := fmt.Sprintf("lock:batch:penalty:%s", time.Now().Format("20060102"))

	// 尝试获取分布式锁，超时时间为20分钟
	if !lock.Acquire(lockKey, 20*time.Minute) {
		// 如果获取锁失败，记录任务跳过状态并返回
		reporter.Report(reporter.JobResult{
			JobName: "PenaltyJob", Status: "SKIPPED", StartTime: time.Now(),
			Detail: "Lock held by another instance",
		})
		return
	}
	// 使用defer确保函数退出时释放锁
	defer lock.Release(lockKey)

	// 使用reporter包装任务执行，便于记录任务状态
	reporter.RunWithReport("PenaltyJob", func() error {
		// 构建罚金计算API的URL
		url := fmt.Sprintf("%s/api/internal/post-loan/penalty/calculate", cfg.PostLoanServiceURL)

		// 构造请求体，包含计算日期和触发源
		body := []byte(fmt.Sprintf(`{"calcDate":"%s","triggerSource":"scheduler"}`, time.Now().Format("2006-01-02")))
		// 设置请求头
		headers := map[string]string{
			"Content-Type":     "application/json",
			"X-Internal-Token": "batch-scheduler",
		}

		// 带重试机制发送HTTP请求，最多重试3次
		resp, err := httpPostWithRetry(url, body, headers, 3)
		if err != nil {
			return fmt.Errorf("penalty calculation failed: %w", err)
		}
		// 确保响应体被关闭
		defer resp.Body.Close()
		// 记录任务完成状态
		log.Printf("[PenaltyJob] Completed. Status: %s\n", resp.Status)
		return nil
	})
}
