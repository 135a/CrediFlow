// Package gateway 封装 batch-service 与 fund-channel-gateway 的内网通信。
//
// 内网签名同时兼容 Java crediflow-common 的 InternalAuthRequestInterceptor：
// dataToSign = path + millis_timestamp, signature = Base64(HMAC-SHA256)。
// 这样无需在网关侧维护两套校验逻辑，定时代扣链路与 Java Feign 调用走同一条规则。
package gateway

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"crediflow/batch-service/internalsign"

	"github.com/google/uuid"
)

const (
	HeaderTraceID    = "X-Trace-Id"
	HeaderRequestID  = "X-Request-Id"
	WithholdEndpoint = "/internal/v1/withhold"
)

// SubmitRequest 与 Go fund-channel-gateway 的 api.SubmitRequest 保持字段一致。
type SubmitRequest struct {
	ProviderID      string            `json:"providerId,omitempty"`
	BusinessOrderNo string            `json:"businessOrderNo"`
	UserID          string            `json:"userId"`
	BindCardID      string            `json:"bindCardId"`
	Amount          string            `json:"amount"`
	Currency        string            `json:"currency,omitempty"`
	Installments    int               `json:"installments,omitempty"`
	TriggerSource   string            `json:"triggerSource,omitempty"`
	Extra           map[string]string `json:"extra,omitempty"`
}

// SubmitResponse 与 fund-channel-gateway 的 api.SubmitResponse 对齐。
type SubmitResponse struct {
	State           string `json:"state"`
	GatewayReqID    string `json:"gatewayRequestId"`
	ProviderID      string `json:"providerId"`
	BusinessOrderNo string `json:"businessOrderNo"`
	ErrorCode       string `json:"errorCode,omitempty"`
	ErrorMessage    string `json:"errorMessage,omitempty"`
}

// Client 是内网签名 + 超时控制的 HTTP 客户端。
// 不内置重试：调度链路偏好「失败立即返回 + 下一周期补偿」，避免对资金方多次受理。
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

// Withhold 调用 fund-channel-gateway 的代扣受理接口。
// 返回的 (resp, httpStatus, err)：err 非空表示传输/编解码层失败；
// httpStatus 用于上层区分 4xx（业务拒绝、不重试）与 5xx（瞬时故障，可下次再扫）。
func (c *Client) Withhold(req SubmitRequest, traceID string) (*SubmitResponse, int, error) {
	return c.post(WithholdEndpoint, req, traceID)
}

func (c *Client) post(path string, body any, traceID string) (*SubmitResponse, int, error) {
	payload, err := json.Marshal(body)
	if err != nil {
		return nil, 0, fmt.Errorf("marshal payload: %w", err)
	}

	httpReq, err := http.NewRequest(http.MethodPost, c.baseURL+path, bytes.NewReader(payload))
	if err != nil {
		return nil, 0, fmt.Errorf("build request: %w", err)
	}
	if traceID == "" {
		traceID = uuid.NewString()
	}
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set(HeaderTraceID, traceID)
	httpReq.Header.Set(HeaderRequestID, traceID)
	internalsign.Apply(httpReq, path, c.secret)

	resp, err := c.httpClient.Do(httpReq)
	if err != nil {
		return nil, 0, fmt.Errorf("call gateway: %w", err)
	}
	defer resp.Body.Close()

	raw, _ := io.ReadAll(resp.Body)
	out := &SubmitResponse{}
	if len(raw) > 0 {
		if err := json.Unmarshal(raw, out); err != nil {
			return nil, resp.StatusCode, fmt.Errorf("decode response (status=%d body=%s): %w", resp.StatusCode, truncate(raw), err)
		}
	}
	return out, resp.StatusCode, nil
}

func truncate(b []byte) string {
	const max = 256
	if len(b) <= max {
		return string(b)
	}
	return string(b[:max]) + "...(truncated)"
}
