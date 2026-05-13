package api

import (
	"crypto/hmac"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"crediflow/fund-channel-gateway/config"
	"crediflow/fund-channel-gateway/logger"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

const (
	headerTraceID       = "X-Trace-Id"
	headerRequestID     = "X-Request-Id"
	headerJavaTimestamp = "X-Timestamp"
	headerTimestamp     = "X-Internal-Timestamp"
	headerNonce         = "X-Internal-Nonce"
	ctxKeyTrace         = "trace_id"
)

// TraceMiddleware ensures every request carries a trace id end-to-end, generating
// one if Java forgot. The id is echoed back so callers can correlate logs.
func TraceMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		trace := c.GetHeader(headerTraceID)
		if trace == "" {
			trace = c.GetHeader(headerRequestID)
		}
		if trace == "" {
			trace = uuid.NewString()
		}
		c.Set(ctxKeyTrace, trace)
		c.Writer.Header().Set(headerTraceID, trace)
		c.Next()
	}
}

// InternalSignMiddleware enforces internal-api-security on the Java→Go boundary.
// It MUST be installed on every /internal/* route. Production refuses to bypass.
func InternalSignMiddleware(cfg config.InternalSignConf) gin.HandlerFunc {
	skew := time.Duration(cfg.TimestampSkewS) * time.Second
	headerName := cfg.HeaderName
	secret := []byte(cfg.SharedSecret)
	disabled := cfg.Disabled
	return func(c *gin.Context) {
		if disabled {
			c.Next()
			return
		}
		sign := c.GetHeader(headerName)
		if sign == "" {
			abort(c, http.StatusUnauthorized, "INTERNAL_SIGN_MISSING", "missing "+headerName)
			return
		}
		// Mode A — 与 Java crediflow-common InternalAuthRequestInterceptor 一致：
		// dataToSign = feignPath + X-Timestamp（毫秒字符串），签名为 Base64(HMAC-SHA256)。
		if javaTs := c.GetHeader(headerJavaTimestamp); javaTs != "" {
			if err := verifyJavaInternalSign(c, secret, javaTs, sign, skew); err != nil {
				abort(c, http.StatusUnauthorized, "INTERNAL_SIGN_INVALID", err.Error())
				return
			}
			c.Next()
			return
		}
		// Mode B — 网关原生：秒级时间戳 + nonce + hex(HMAC)。
		tsRaw := c.GetHeader(headerTimestamp)
		nonce := c.GetHeader(headerNonce)
		if tsRaw == "" || nonce == "" {
			abort(c, http.StatusUnauthorized, "INTERNAL_SIGN_MISSING", "need X-Timestamp (Java) or X-Internal-Timestamp + X-Internal-Nonce")
			return
		}
		ts, err := strconv.ParseInt(tsRaw, 10, 64)
		if err != nil {
			abort(c, http.StatusUnauthorized, "INTERNAL_TS_INVALID", "timestamp not a unix epoch second")
			return
		}
		drift := time.Since(time.Unix(ts, 0))
		if drift < -skew || drift > skew {
			abort(c, http.StatusUnauthorized, "INTERNAL_TS_SKEW", fmt.Sprintf("timestamp drift %s exceeds %s", drift, skew))
			return
		}
		if err := verifyInternalSign(c, secret, tsRaw, nonce, sign); err != nil {
			abort(c, http.StatusUnauthorized, "INTERNAL_SIGN_INVALID", err.Error())
			return
		}
		c.Next()
	}
}

func verifyJavaInternalSign(c *gin.Context, secret []byte, tsMillis, signB64 string, skew time.Duration) error {
	ms, err := strconv.ParseInt(tsMillis, 10, 64)
	if err != nil {
		return fmt.Errorf("X-Timestamp not numeric: %w", err)
	}
	drift := time.Since(time.UnixMilli(ms))
	if drift < -skew || drift > skew {
		return fmt.Errorf("timestamp drift %s exceeds %s", drift, skew)
	}
	path := c.Request.URL.Path
	data := path + tsMillis
	mac := hmac.New(sha256.New, secret)
	_, _ = mac.Write([]byte(data))
	expected := base64.StdEncoding.EncodeToString(mac.Sum(nil))
	if constantTimeStringEq(expected, signB64) {
		return nil
	}
	return errors.New("java-style hmac mismatch")
}

func constantTimeStringEq(a, b string) bool {
	ba, bb := []byte(a), []byte(b)
	if len(ba) != len(bb) {
		return false
	}
	return subtle.ConstantTimeCompare(ba, bb) == 1
}

func verifyInternalSign(c *gin.Context, secret []byte, ts, nonce, sign string) error {
	// Canonical string: METHOD\nPATH\nTIMESTAMP\nNONCE
	canonical := c.Request.Method + "\n" + c.FullPath() + "\n" + ts + "\n" + nonce
	mac := hmac.New(sha256.New, secret)
	_, _ = mac.Write([]byte(canonical))
	expected := hex.EncodeToString(mac.Sum(nil))
	if !hmac.Equal([]byte(expected), []byte(sign)) {
		return errors.New("hmac mismatch")
	}
	return nil
}

// RecoveryMiddleware turns panics into structured 500 responses so a single buggy
// handler cannot bring down the gateway in production.
func RecoveryMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		defer func() {
			if r := recover(); r != nil {
				trace, _ := c.Get(ctxKeyTrace)
				logger.Error("FundGateway", "panic in handler trace=%v: %v", trace, r)
				abort(c, http.StatusInternalServerError, "INTERNAL_PANIC", "internal error")
			}
		}()
		c.Next()
	}
}

func abort(c *gin.Context, status int, code, msg string) {
	trace, _ := c.Get(ctxKeyTrace)
	traceStr, _ := trace.(string)
	c.AbortWithStatusJSON(status, ErrorResponse{Code: code, Message: msg, TraceID: traceStr})
}

func traceOf(c *gin.Context) string {
	v, _ := c.Get(ctxKeyTrace)
	s, _ := v.(string)
	return s
}
