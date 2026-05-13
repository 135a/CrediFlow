package jobs

import (
	"bytes"
	"fmt"
	"io"
	"log"
	"math"
	"net/http"
	"time"
)

// httpPostWithRetry 带指数退避重试的 HTTP POST。
//
// 历史上 deduct_job 与其它 Job 共享这一帮助函数；deduct_job 在批次 3
// 切换到 fund-channel-gateway 客户端后不再使用，但其它 Job（overdue / penalty
// / reminder / risk_dispatch / notification）仍依赖。为避免一次性重构所有
// Job，将其抽取到独立文件维持兼容。
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
