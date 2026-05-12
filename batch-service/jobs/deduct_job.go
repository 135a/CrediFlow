package jobs

import (
	"bytes"
	"crediflow/batch-service/config"
	"fmt"
	"log"
	"net/http"
	"time"
)

// RunDeductJob 模拟代扣任务调度
func RunDeductJob(cfg *config.Config) {
	log.Println("[DeductJob] Started")
	start := time.Now()

	// 10.2 代扣任务：调用还款服务 HTTPS 接口，携带幂等键
	// 这里用 HTTP POST 模拟对 repayment-service 的调用
	url := fmt.Sprintf("%s/api/app/repayment/active-repay", cfg.RepaymentServiceURL)

	// 模拟针对某个 planId 的代扣请求
	planId := "12345"
	idmpToken := fmt.Sprintf("BATCH_DEDUCT_%s_%s", planId, time.Now().Format("20060102"))

	reqBody := []byte(fmt.Sprintf(`planId=%s&idmpToken=%s`, planId, idmpToken))
	req, err := http.NewRequest("POST", url, bytes.NewBuffer(reqBody))
	if err != nil {
		log.Printf("[DeductJob] Error creating request: %v\n", err)
		return
	}

	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("X-User-Id", "999") // 模拟调度系统/系统级用户

	// 实际代码中建议加重试逻辑或配置 HTTP Client 超时
	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)

	if err != nil {
		log.Printf("[DeductJob] Request failed: %v\n", err)
	} else {
		defer resp.Body.Close()
		log.Printf("[DeductJob] Repayment API called. Status: %s\n", resp.Status)
	}

	log.Printf("[DeductJob] Finished in %v\n", time.Since(start))
}
