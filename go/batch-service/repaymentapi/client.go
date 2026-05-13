// Package repaymentapi 是 batch-service 调用 Java repayment-service 内网接口的轻量
// 客户端。所有请求 MUST 经 internalsign 注入 Java 风格内网签名，确保 Java 侧
// InternalAuthFilter 校验通过。
package repaymentapi

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"crediflow/batch-service/internalsign"

	"github.com/google/uuid"
)

const dueTodayPath = "/api/internal/repayment/due-today"

// DueRepayment 是 GET /api/internal/repayment/due-today 返回的精简视图。
// batch-service 仅消费定时代扣链路必要的字段，避免不必要的耦合。
type DueRepayment struct {
	PlanID          int64  `json:"planId"`
	UserID          int64  `json:"userId"`
	ContractID      int64  `json:"contractId,omitempty"`
	ApplicationID   int64  `json:"applicationId,omitempty"`
	Period          int    `json:"period"`
	Amount          string `json:"amount"`
	Currency        string `json:"currency"`
	BindCardID      string `json:"bindCardId,omitempty"`
	BusinessOrderNo string `json:"businessOrderNo"`
}

type dueResponse struct {
	Code    int            `json:"code"`
	Data    []DueRepayment `json:"data"`
	Message string         `json:"message,omitempty"`
}

type Client struct {
	baseURL    string
	secret     []byte
	httpClient *http.Client
}

func NewClient(baseURL, secret string, timeout time.Duration) *Client {
	if timeout <= 0 {
		timeout = 10 * time.Second
	}
	return &Client{
		baseURL:    baseURL,
		secret:     []byte(secret),
		httpClient: &http.Client{Timeout: timeout},
	}
}

// ListDueToday 查询当天应代扣的还款计划期次。当批次过大时调用方应自行分页/并发分批。
func (c *Client) ListDueToday(traceID string, limit int) ([]DueRepayment, error) {
	if traceID == "" {
		traceID = uuid.NewString()
	}
	url := fmt.Sprintf("%s%s?limit=%d", c.baseURL, dueTodayPath, limit)
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("X-Trace-Id", traceID)
	req.Header.Set("X-Request-Id", traceID)
	internalsign.Apply(req, dueTodayPath, c.secret)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("call repayment-service: %w", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("repayment-service status=%d body=%s", resp.StatusCode, truncate(body))
	}
	var out dueResponse
	if err := json.Unmarshal(body, &out); err != nil {
		return nil, fmt.Errorf("decode body: %w", err)
	}
	if out.Code != 0 && out.Code != 200 {
		return nil, fmt.Errorf("repayment-service biz error code=%d msg=%s", out.Code, out.Message)
	}
	return out.Data, nil
}

func truncate(b []byte) string {
	const max = 256
	if len(b) <= max {
		return string(b)
	}
	return string(b[:max]) + "...(truncated)"
}
