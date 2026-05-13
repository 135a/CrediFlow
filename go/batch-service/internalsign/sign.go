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
func Apply(req *http.Request, path string, secret []byte) {
	ts := strconv.FormatInt(time.Now().UnixMilli(), 10)
	mac := hmac.New(sha256.New, secret)
	_, _ = mac.Write([]byte(path + ts))
	sig := base64.StdEncoding.EncodeToString(mac.Sum(nil))
	req.Header.Set(HeaderTimestamp, ts)
	req.Header.Set(HeaderSignature, sig)
}
