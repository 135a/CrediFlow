// Package internalsign 提供与 Java crediflow-common InternalAuthRequestInterceptor
// 完全一致的内网签名实现：dataToSign = path + 毫秒时间戳，
// signature = Base64(HMAC-SHA256(dataToSign, secret))。
//
// 所有 batch-service 出向的内部 HTTP 调用 MUST 调用 Apply 注入头部，避免
// 在网关侧维护两套签名校验逻辑。
package internalsign

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"net/http"
	"strconv"
	"time"
)

const (
	HeaderTimestamp = "X-Timestamp"
	HeaderSignature = "X-Internal-Sign"
)

// Apply 给请求附加 Java 风格内网签名头。path 必须与服务端拦截的 URI 完全一致
// （含 query string 之外的部分）。
// Apply 函数用于为HTTP请求添加时间戳和签名
// 参数:
//
//	req - 指向HTTP请求的指针
//	path - 请求路径
//	secret - 用于签名的密钥
func Apply(req *http.Request, path string, secret []byte) {
	// 获取当前时间的Unix毫秒时间戳
	ts := strconv.FormatInt(time.Now().UnixMilli(), 10)
	// 使用HMAC-SHA256算法创建新的MAC(消息认证码)
	mac := hmac.New(sha256.New, secret)
	// 将路径和时间戳写入MAC计算器
	_, _ = mac.Write([]byte(path + ts))
	// 计算MAC值并将其转换为Base64编码的字符串
	sig := base64.StdEncoding.EncodeToString(mac.Sum(nil))
	// 将时间戳添加到HTTP请求头中
	req.Header.Set(HeaderTimestamp, ts)
	// 将签名添加到HTTP请求头中
	req.Header.Set(HeaderSignature, sig)
}
