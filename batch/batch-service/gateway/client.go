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
	// ProviderID 表示提供商ID，使用omitempty标签表示在JSON序列化时如果为空则不包含该字段
	ProviderID string `json:"providerId,omitempty"`
	// BusinessOrderNo 表示业务订单号，是必填字段
	BusinessOrderNo string `json:"businessOrderNo"`
	// UserID 表示用户ID，是必填字段
	UserID string `json:"userId"`
	// BindCardID 表示绑定的卡片ID，是必填字段
	BindCardID string `json:"bindCardId"`
	// Amount 表示交易金额，是必填字段
	Amount string `json:"amount"`
	// Currency 表示货币类型，使用omitempty标签表示在JSON序列化时如果为空则不包含该字段
	Currency string `json:"currency,omitempty"`
	// Installments 表示分期数，使用omitempty标签表示在JSON序列化时如果为空则不包含该字段
	Installments int `json:"installments,omitempty"`
	// TriggerSource 表示触发来源，使用omitempty标签表示在JSON序列化时如果为空则不包含该字段
	TriggerSource string `json:"triggerSource,omitempty"`
	// Extra 表示额外信息，使用map存储键值对，使用omitempty标签表示在JSON序列化时如果为空则不包含该字段
	Extra map[string]string `json:"extra,omitempty"`
}

// SubmitResponse 与 fund-channel-gateway 的 api.SubmitResponse 对齐。
// SubmitResponse 定义了提交响应的结构体，包含处理结果和相关信息
type SubmitResponse struct {
	State           string `json:"state"`            // 处理状态，表示请求的处理结果
	GatewayReqID    string `json:"gatewayRequestId"` // 网关请求ID，用于唯一标识请求
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

// NewClient 创建一个新的客户端实例
// 参数:
//   - baseURL: API的基础URL
//   - secret: 用于认证的密钥
//   - timeout: 请求超时时间
//
// 返回值:
//   - *Client: 新创建的客户端实例
func NewClient(baseURL, secret string, timeout time.Duration) *Client {
	// 检查超时时间是否有效，如果小于等于0则设置为默认的10秒
	if timeout <= 0 {
		timeout = 10 * time.Second
	}
	// 返回一个新的客户端实例，包含基础URL、密钥和HTTP客户端
	return &Client{
		baseURL:    baseURL,
		secret:     []byte(secret),
		httpClient: &http.Client{Timeout: timeout},
	}
}

// Withhold 调用 fund-channel-gateway 的代扣受理接口。
// 返回的 (resp, httpStatus, err)：err 非空表示传输/编解码层失败；
// httpStatus 用于上层区分 4xx（业务拒绝、不重试）与 5xx（瞬时故障，可下次再扫）。
// Withhold 方法是 Client 结构体的一个方法，用于执行扣款操作
// 参数:
//
//	req - SubmitRequest 类型的请求参数，包含扣款所需的信息
//	traceID - 用于追踪请求的唯一标识符
//
// 返回值:
//
//	*SubmitResponse - 扣款操作的响应结果
//	int - HTTP状态码
//	error - 操作过程中可能出现的错误
func (c *Client) Withhold(req SubmitRequest, traceID string) (*SubmitResponse, int, error) {
	// 调用 Client 的 post 方法，向 WithholdEndpoint 发送 POST 请求
	// 传入请求参数 req 和追踪ID traceID
	return c.post(WithholdEndpoint, req, traceID)
}

// post 是 Client 结构体的一个方法，用于发送 POST 请求到指定路径
// 参数:
//   - path: 请求的路径
//   - body: 请求体的内容，会被序列化为 JSON
//   - traceID: 请求追踪 ID，如果为空则会生成一个新的
//
// 返回值:
//   - *SubmitResponse: 服务器响应的结构体指针
//   - int: HTTP 响应状态码
//   - error: 请求过程中发生的错误
func (c *Client) post(path string, body any, traceID string) (*SubmitResponse, int, error) {
	// 将请求体序列化为 JSON 格式
	payload, err := json.Marshal(body)
	if err != nil {
		return nil, 0, fmt.Errorf("marshal payload: %w", err)
	}

	// 创建一个新的 HTTP 请求
	httpReq, err := http.NewRequest(http.MethodPost, c.baseURL+path, bytes.NewReader(payload))
	if err != nil {
		return nil, 0, fmt.Errorf("build request: %w", err)
	}
	// 如果 traceID 为空，则生成一个新的 UUID 作为追踪 ID
	if traceID == "" {
		traceID = uuid.NewString()
	}

	// 设置请求头
	httpReq.Header.Set("Content-Type", "application/json") // 设置内容类型为 JSON
	httpReq.Header.Set(HeaderTraceID, traceID)             // 设置追踪 ID
	httpReq.Header.Set(HeaderRequestID, traceID)           // 设置请求 ID
	internalsign.Apply(httpReq, path, c.secret)            // 应用内部签名

	// 发送 HTTP 请求
	resp, err := c.httpClient.Do(httpReq)
	if err != nil {
		return nil, 0, fmt.Errorf("call gateway: %w", err)
	}
	defer resp.Body.Close() // 确保响应体被关闭

	// 读取响应体
	raw, _ := io.ReadAll(resp.Body)
	out := &SubmitResponse{}
	if len(raw) > 0 {
		// 如果响应体不为空，则尝试将其反序列化为 SubmitResponse
		if err := json.Unmarshal(raw, out); err != nil {
			return nil, resp.StatusCode, fmt.Errorf("decode response (status=%d body=%s): %w", resp.StatusCode, truncate(raw), err)
		}
	}
	return out, resp.StatusCode, nil // 返回响应、状态码和可能的错误
}

// truncate 函数将字节数组转换为字符串，如果长度超过最大限制则进行截断
// 参数 b: 需要转换的字节数组
// 返回值: 转换后的字符串，如果原始字节数组长度超过max，则添加"...(truncated)"标记
func truncate(b []byte) string {
	const max = 256    // 定义截断的最大长度常量
	if len(b) <= max { // 检查字节数组长度是否不超过最大限制
		return string(b) // 如果不超过，直接转换为字符串返回
	}
	return string(b[:max]) + "...(truncated)" // 如果超过，截断并添加标记后返回
}
