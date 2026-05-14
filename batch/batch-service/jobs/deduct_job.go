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
func RunDeductJob(cfg *config.Config) {
	lockKey := fmt.Sprintf("lock:batch:deduct:%s", time.Now().Format("20060102"))

	if !lock.Acquire(lockKey, 30*time.Minute) {
		reporter.Report(reporter.JobResult{
			JobName: "DeductJob", Status: "SKIPPED", StartTime: time.Now(),
			Detail: "Lock held by another instance",
		})
		return
	}
	defer lock.Release(lockKey)

	reporter.RunWithReport("DeductJob", func() error {
		traceID := uuid.NewString()
		log.Printf("[DeductJob] start trace=%s\n", traceID)

		repayClient := repaymentapi.NewClient(cfg.RepaymentServiceURL, cfg.FundGatewaySecret, 15*time.Second)
		due, err := repayClient.ListDueToday(traceID, cfg.DeductMaxBatch)
		if err != nil {
			return fmt.Errorf("fetch due-today: %w", err)
		}
		log.Printf("[DeductJob] %d due plans fetched\n", len(due))
		if len(due) == 0 {
			return nil
		}

		gw := gateway.NewClient(cfg.FundGatewayURL, cfg.FundGatewaySecret, 10*time.Second)
		summary := dispatch(due, gw, cfg, traceID)
		log.Printf("[DeductJob] summary trace=%s %s\n", traceID, summary.line())
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

func dispatch(due []repaymentapi.DueRepayment, gw *gateway.Client, cfg *config.Config, traceID string) *dispatchSummary {
	summary := &dispatchSummary{}
	concurrency := cfg.DeductConcurrency
	if concurrency <= 0 {
		concurrency = 4
	}
	if concurrency > len(due) {
		concurrency = len(due)
	}
	sem := make(chan struct{}, concurrency)
	var wg sync.WaitGroup
	for _, plan := range due {
		wg.Add(1)
		sem <- struct{}{}
		go func(p repaymentapi.DueRepayment) {
			defer wg.Done()
			defer func() { <-sem }()
			withholdOne(p, gw, cfg, traceID, summary)
		}(plan)
	}
	wg.Wait()
	return summary
}

func withholdOne(p repaymentapi.DueRepayment, gw *gateway.Client, cfg *config.Config, parentTrace string, s *dispatchSummary) {
	atomic.AddInt64(&s.total, 1)
	req := gateway.SubmitRequest{
		ProviderID:      cfg.FundGatewayDefaultProviderID,
		BusinessOrderNo: p.BusinessOrderNo,
		UserID:          fmt.Sprintf("%d", p.UserID),
		BindCardID:      p.BindCardID,
		Amount:          p.Amount,
		Currency:        defaultIfBlank(p.Currency, "CNY"),
		Installments:    p.Period,
		TriggerSource:   "scheduler",
		Extra: map[string]string{
			"planId":        fmt.Sprintf("%d", p.PlanID),
			"applicationId": fmt.Sprintf("%d", p.ApplicationID),
			"contractId":    fmt.Sprintf("%d", p.ContractID),
			"parentTrace":   parentTrace,
		},
	}
	traceID := parentTrace + ":plan-" + fmt.Sprintf("%d", p.PlanID)

	start := time.Now()
	resp, status, err := gw.Withhold(req, traceID)
	latency := time.Since(start)
	atomic.AddInt64(&s.totalLatency, int64(latency))
	for {
		cur := atomic.LoadInt64(&s.maxLatency)
		if int64(latency) <= cur || atomic.CompareAndSwapInt64(&s.maxLatency, cur, int64(latency)) {
			break
		}
	}

	switch {
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
