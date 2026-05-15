package jobs

import (
	"fmt"
	"log"
	"sync"
	"sync/atomic"
	"time"

	"crediflow/batch-service/config"
	"crediflow/batch-service/gateway"
	"crediflow/batch-service/lock"
	"crediflow/batch-service/repaymentapi"
	"crediflow/batch-service/reporter"

	"github.com/google/uuid"
)

// RunDeductJob 每日定时代扣。
//
// 阶段 3 重构（对齐 fund-provider-go-gateway 任务 9.x）：
//  1. 抢分布式锁，避免多实例并发执行；
//  2. 调用 Java repayment-service /api/internal/repayment/due-today 拉取待代扣期次；
//  3. 对每个期次并发调用 Go fund-channel-gateway /internal/v1/withhold 受理；
//  4. 真正的扣款终态由网关异步桥接 REPAYMENT_SETTLED_EVENT 推进，
//     本 Job 仅负责「受理 + 指标统计」，不直接调用资金方。
//
// 关键指标输出（任务 9.2）：accepted / rejected / circuitOpen / transportErr。
// 任何 5xx 或熔断 MUST 在结果摘要中可见，便于 reporter -> 监控告警链路消费。
// RunDeductJob 是一个执行扣款任务的函数
// 它会获取锁以确保同一时间只有一个实例在运行，然后执行扣款操作
// 参数:
//
//	cfg: 配置信息，包含服务URL、密钥等
func RunDeductJob(cfg *config.Config) {
	// 生成基于当前日期的锁键，格式为 "lock:batch:deduct:YYYYMMDD"
	lockKey := fmt.Sprintf("lock:batch:deduct:%s", time.Now().Format("20060102"))

	// 尝试获取锁，如果获取失败则跳过本次执行
	if !lock.Acquire(lockKey, 30*time.Minute) {
		// 记录任务被跳过的情况
		reporter.Report(reporter.JobResult{
			JobName: "DeductJob", Status: "SKIPPED", StartTime: time.Now(),
			Detail: "Lock held by another instance",
		})
		return
	}
	// 使用 defer 确保函数退出时释放锁
	defer lock.Release(lockKey)

	// 使用 reporter 记录任务执行情况
	reporter.RunWithReport("DeductJob", func() error {
		// 生成唯一的跟踪ID，用于日志追踪
		traceID := uuid.NewString()
		log.Printf("[DeductJob] start trace=%s\n", traceID)

		// 创建还款服务客户端
		repayClient := repaymentapi.NewClient(cfg.RepaymentServiceURL, cfg.FundGatewaySecret, 15*time.Second)
		// 获取今日到期的还款计划
		due, err := repayClient.ListDueToday(traceID, cfg.DeductMaxBatch)
		if err != nil {
			return fmt.Errorf("fetch due-today: %w", err)
		}
		log.Printf("[DeductJob] %d due plans fetched\n", len(due))
		// 如果没有到期的还款计划，直接返回
		if len(due) == 0 {
			return nil
		}

		// 创建资金网关客户端
		gw := gateway.NewClient(cfg.FundGatewayURL, cfg.FundGatewaySecret, 10*time.Second)
		// 分发扣款任务
		summary := dispatch(due, gw, cfg, traceID)
		log.Printf("[DeductJob] summary trace=%s %s\n", traceID, summary.line())
		// 如果有传输错误或电路断开的情况，返回错误以触发告警
		if summary.transportErr > 0 || summary.circuitOpen > 0 {
			// 让 reporter 标记为 FAILED，触发上游监控告警。
			return fmt.Errorf("deduct dispatch has issues: %s", summary.line())
		}
		return nil
	})
}

type dispatchSummary struct {
	total        int64
	accepted     int64
	duplicated   int64
	rejected     int64
	circuitOpen  int64
	transportErr int64
	totalLatency int64 // ns
	maxLatency   int64 // ns
}

func (s *dispatchSummary) line() string {
	avg := time.Duration(0)
	if s.total > 0 {
		avg = time.Duration(s.totalLatency / s.total)
	}
	return fmt.Sprintf("total=%d accepted=%d dup=%d rejected=%d circuit=%d transport=%d avg=%s max=%s",
		s.total, s.accepted, s.duplicated, s.rejected, s.circuitOpen, s.transportErr,
		avg.Truncate(time.Millisecond), time.Duration(s.maxLatency).Truncate(time.Millisecond))
}

// dispatch 是一个分发函数，用于处理待还款计划
// 参数:
//   - due: 待还款计划列表
//   - gw: 网关客户端
//   - cfg: 配置信息
//   - traceID: 追踪ID
//
// 返回值:
//   - *dispatchSummary: 分发摘要，包含处理结果
func dispatch(due []repaymentapi.DueRepayment, gw *gateway.Client, cfg *config.Config, traceID string) *dispatchSummary {
	summary := &dispatchSummary{} // 初始化分发摘要结构体
	// 获取配置中的并发数，如果小于等于0则默认为4
	concurrency := cfg.DeductConcurrency
	if concurrency <= 0 {
		concurrency = 4
	}
	// 如果并发数大于待处理计划数量，则调整为待处理计划数量
	if concurrency > len(due) {
		concurrency = len(due)
	}
	// 创建信号量通道，用于控制并发数量
	sem := make(chan struct{}, concurrency)
	var wg sync.WaitGroup // 创建等待组，用于等待所有goroutine完成
	// 遍历待还款计划，为每个计划启动一个goroutine进行处理
	for _, plan := range due {
		wg.Add(1)         // 增加等待组的计数器
		sem <- struct{}{} // 向信号量通道发送一个空结构体，表示占用一个并发槽
		go func(p repaymentapi.DueRepayment) {
			defer wg.Done()                           // 确保在函数执行完成后减少等待组计数器
			defer func() { <-sem }()                  // 确保在函数执行完成后释放一个并发槽
			withholdOne(p, gw, cfg, traceID, summary) // 处理单个还款计划
		}(plan)
	}
	wg.Wait()      // 等待所有goroutine完成
	return summary // 返回处理结果摘要
}

// withholdOne 执行一次扣款操作，处理扣款请求并更新统计信息
// 参数:
//   p: repaymentapi.DueRepayment - 待扣款还款信息
//   gw: *gateway.Client - 网关客户端，用于发送扣款请求
//   cfg: *config.Config - 配置信息，包含默认提供商ID等
//   parentTrace: string - 父级追踪ID，用于请求链路追踪
//   s: *dispatchSummary - 调度摘要，用于记录各种统计信息

// 增加总请求数
func withholdOne(p repaymentapi.DueRepayment, gw *gateway.Client, cfg *config.Config, parentTrace string, s *dispatchSummary) {
	// 构建扣款请求
	atomic.AddInt64(&s.total, 1)
	req := gateway.SubmitRequest{ // 设置默认提供商ID
		ProviderID:      cfg.FundGatewayDefaultProviderID,  // 业务订单号
		BusinessOrderNo: p.BusinessOrderNo,                 // 用户ID
		UserID:          fmt.Sprintf("%d", p.UserID),       // 绑定卡ID
		BindCardID:      p.BindCardID,                      // 扣款金额
		Amount:          p.Amount,                          // 货币类型，默认为CNY
		Currency:        defaultIfBlank(p.Currency, "CNY"), // 分期数
		Installments:    p.Period,                          // 触发源为调度器
		TriggerSource:   "scheduler",                       // 额外信息
		Extra: map[string]string{ // 计划ID
			"planId":        fmt.Sprintf("%d", p.PlanID),        // 申请ID
			"applicationId": fmt.Sprintf("%d", p.ApplicationID), // 合同ID
			"contractId":    fmt.Sprintf("%d", p.ContractID),    // 父级追踪ID
			"parentTrace":   parentTrace,
		},
		// 构建追踪ID
	}
	traceID := parentTrace + ":plan-" + fmt.Sprintf("%d", p.PlanID)
	// 记录请求开始时间

	// 发送扣款请求
	start := time.Now()
	// 计算请求耗时
	resp, status, err := gw.Withhold(req, traceID)
	// 更新总耗时统计
	latency := time.Since(start)
	// 更新最大耗时统计（使用原子操作确保线程安全）
	atomic.AddInt64(&s.totalLatency, int64(latency))
	for {
		cur := atomic.LoadInt64(&s.maxLatency)
		if int64(latency) <= cur || atomic.CompareAndSwapInt64(&s.maxLatency, cur, int64(latency)) {
			break
		}
	}
	// 根据响应状态进行不同处理

	switch {
	// 传输错误处理
	case err != nil:
		atomic.AddInt64(&s.transportErr, 1)
		log.Printf("[DeductJob] planId=%d transport error: %v\n", p.PlanID, err)
	case status >= 200 && status < 300:
		switch resp.State {
		case "ACCEPTED":
			if resp.ErrorCode == "DUPLICATE_RECEIPT" {
				atomic.AddInt64(&s.duplicated, 1)
			} else {
				atomic.AddInt64(&s.accepted, 1)
			}
		default:
			atomic.AddInt64(&s.rejected, 1)
			log.Printf("[DeductJob] planId=%d gateway responded state=%s code=%s\n",
				p.PlanID, resp.State, resp.ErrorCode)
		}
	case status == 503 && resp != nil && resp.ErrorCode == "PROVIDER_CIRCUIT_OPEN":
		atomic.AddInt64(&s.circuitOpen, 1)
		log.Printf("[DeductJob] planId=%d circuit open, will retry next cycle\n", p.PlanID)
	case status >= 500:
		atomic.AddInt64(&s.transportErr, 1)
		log.Printf("[DeductJob] planId=%d gateway 5xx status=%d code=%s msg=%s\n",
			p.PlanID, status, resp.ErrorCode, resp.ErrorMessage)
	default:
		atomic.AddInt64(&s.rejected, 1)
		log.Printf("[DeductJob] planId=%d gateway rejected status=%d code=%s msg=%s\n",
			p.PlanID, status, resp.ErrorCode, resp.ErrorMessage)
	}
}

func defaultIfBlank(v, fallback string) string {
	if v == "" {
		return fallback
	}
	return v
}
