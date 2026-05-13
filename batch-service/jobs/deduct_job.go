package jobs

import (
	"bytes"
	"crediflow/batch-service/config"
	"crediflow/batch-service/lock"
	"crediflow/batch-service/reporter"
	"fmt"
	"io"
	"log"
	"math"
	"net/http"
	"time"
)

// httpPostWithRetry 带指数退避重试的 HTTP POST
func httpPostWithRetry(url string, body []byte, headers map[string]string, maxRetries int) (*http.Response, error) {
	client := &http.Client{Timeout: 15 * time.Second}

	var lastErr error
	for attempt := 0; attempt <= maxRetries; attempt++ {
		if attempt > 0 {
			backoff := time.Duration(math.Pow(2, float64(attempt))) * time.Second
			log.Printf("[HTTP] Retry %d/%d after %v...\n", attempt, maxRetries, backoff)
			time.Sleep(backoff)
		}

		req, err := http.NewRequest("POST", url, bytes.NewBuffer(body))
		if err != nil {
			return nil, fmt.Errorf("create request failed: %w", err)
		}

		for k, v := range headers {
			req.Header.Set(k, v)
		}

		resp, err := client.Do(req)
		if err != nil {
			lastErr = err
			log.Printf("[HTTP] Attempt %d failed: %v\n", attempt+1, err)
			continue
		}

		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			return resp, nil
		}

		respBody, _ := io.ReadAll(resp.Body)
		resp.Body.Close()
		lastErr = fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(respBody))
		log.Printf("[HTTP] Attempt %d got status %d\n", attempt+1, resp.StatusCode)
	}

	return nil, fmt.Errorf("all %d retries exhausted: %w", maxRetries+1, lastErr)
}

// RunDeductJob 自动代扣还款任务（带分布式锁 + 重试）
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
		url := fmt.Sprintf("%s/api/internal/repayment/batch-deduct", cfg.RepaymentServiceURL)
		idmpToken := fmt.Sprintf("BATCH_DEDUCT_%s", time.Now().Format("20060102"))

		body := []byte(fmt.Sprintf(`{"idmpToken":"%s","triggerSource":"scheduler"}`, idmpToken))
		headers := map[string]string{
			"Content-Type":     "application/json",
			"X-Internal-Token": "batch-scheduler",
		}

		resp, err := httpPostWithRetry(url, body, headers, 3)
		if err != nil {
			return fmt.Errorf("deduct batch call failed: %w", err)
		}
		defer resp.Body.Close()
		log.Printf("[DeductJob] Completed. Status: %s\n", resp.Status)
		return nil
	})
}


