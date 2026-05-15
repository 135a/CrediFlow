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
// RunRiskDispatchJob 执行风险调度任务，负责将用户数据投递到数据代理服务进行风险评估
// @param cfg 配置信息，包含数据代理服务的URL等配置
func RunRiskDispatchJob(cfg *config.Config) {
	// 生成基于当前日期的分布式锁键，确保同一时间只有一个实例执行该任务
	lockKey := fmt.Sprintf("lock:batch:risk_dispatch:%s", time.Now().Format("20060102"))

	// 尝试获取分布式锁，如果获取失败则跳过本次执行
	if !lock.Acquire(lockKey, 30*time.Minute) {
		// 记录任务被跳过的状态报告
		reporter.Report(reporter.JobResult{
			JobName: "RiskDispatchJob", Status: "SKIPPED", StartTime: time.Now(),
			Detail: "Lock held by another instance",
		})
		return
	}
	// 使用defer确保锁一定会被释放
	defer lock.Release(lockKey)

	// 使用reporter.RunWithReport执行任务并记录执行结果
	reporter.RunWithReport("RiskDispatchJob", func() error {
		// 构建数据代理服务的API URL
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
