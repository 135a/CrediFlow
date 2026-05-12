package jobs

import (
	"bytes"
	"crediflow/batch-service/config"
	"fmt"
	"log"
	"net/http"
	"time"
)

// RunOverdueJob 模拟逾期巡检任务
func RunOverdueJob(cfg *config.Config) {
	log.Println("[OverdueJob] Started")
	start := time.Now()

	// 10.3 实现逾期巡检、罚息计算、还款提醒、风控异步投递、消息推送调度任务
	// 这里用 HTTP POST 模拟对 post-loan-service 的调用（内部接口）
	url := fmt.Sprintf("%s/api/internal/post-loan/overdue/process", cfg.PostLoanServiceURL)

	// 模拟针对某个逾期计划的处理
	reqBody := []byte("planId=12345&contractId=888&userId=1001&overdueDays=5&principal=1000.00")
	req, err := http.NewRequest("POST", url, bytes.NewBuffer(reqBody))
	if err != nil {
		log.Printf("[OverdueJob] Error creating request: %v\n", err)
		return
	}

	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	// 这里可以加上内部调用的 JWT Token 或者 mTLS 证书

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)

	if err != nil {
		log.Printf("[OverdueJob] Request failed: %v\n", err)
	} else {
		defer resp.Body.Close()
		log.Printf("[OverdueJob] PostLoan API called. Status: %s\n", resp.Status)
	}

	log.Printf("[OverdueJob] Finished in %v\n", time.Since(start))
}
