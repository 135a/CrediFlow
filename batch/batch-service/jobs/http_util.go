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
// httpPostWithRetry 带重试机制的HTTP POST请求函数
// 参数:
//
//	url: 请求的目标URL
//	body: 请求体的字节数据
//	headers: 请求头映射表
//	maxRetries: 最大重试次数
//
// 返回值:
//
//	*http.Response: HTTP响应对象
//	error: 错误信息
func httpPostWithRetry(url string, body []byte, headers map[string]string, maxRetries int) (*http.Response, error) {
	// 创建HTTP客户端，设置15秒超时
	client := &http.Client{Timeout: 15 * time.Second}

	// 记录最后一次发生的错误
	var lastErr error
	// 循环尝试请求，最多尝试maxRetries+1次（初始请求+maxRetries次重试）
	for attempt := 0; attempt <= maxRetries; attempt++ {
		// 如果不是第一次尝试，则进行指数退避重试
		if attempt > 0 {
			// 计算退避时间：2^attempt 秒
			backoff := time.Duration(math.Pow(2, float64(attempt))) * time.Second
			log.Printf("[HTTP] Retry %d/%d after %v...\n", attempt, maxRetries, backoff)
			time.Sleep(backoff)
		}

		// 创建新的POST请求
		req, err := http.NewRequest("POST", url, bytes.NewBuffer(body))
		if err != nil {
			return nil, fmt.Errorf("create request failed: %w", err)
		}

		// 设置请求头
		for k, v := range headers {
			req.Header.Set(k, v)
		}

		// 发送请求
		resp, err := client.Do(req)
		if err != nil {
			// 记录错误并继续下一次尝试
			lastErr = err
			log.Printf("[HTTP] Attempt %d failed: %v\n", attempt+1, err)
			continue
		}

		// 检查响应状态码是否在2xx范围内
		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			return resp, nil
		}

		// 读取响应体
		respBody, _ := io.ReadAll(resp.Body)
		resp.Body.Close()
		// 记录错误并继续下一次尝试
		lastErr = fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(respBody))
		log.Printf("[HTTP] Attempt %d got status %d\n", attempt+1, resp.StatusCode)
	}

	// 所有重试尝试都失败后，返回最后一次错误
	return nil, fmt.Errorf("all %d retries exhausted: %w", maxRetries+1, lastErr)
}
