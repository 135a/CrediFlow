package audit

import (
	"crypto/sha256"
	"encoding/hex"
	"time"

	"crediflow/fund-channel-gateway/logger"
)

// Direction distinguishes outbound calls to the fund provider from inbound
// callbacks the provider POSTs back to us. Audit lines are written for both.
type Direction string

const (
	OutboundCall Direction = "OUT"
	InboundCB    Direction = "CB"
)

type Entry struct {
	TraceID        string
	GatewayReqID   string
	ProviderID     string
	Operation      string
	Direction      Direction
	HTTPStatus     int
	SignatureValid bool
	CircuitOpen    bool
	PayloadDigest  string
	DurationMs     int64
	ErrorCode      string
	BusinessKey    string
	ProviderTxnNo  string
}

type Recorder interface {
	Record(Entry)
}

// stdoutRecorder is the default in-process recorder. It emits structured
// log lines without ever including the raw payload. In phase >=3 this will
// be replaced or augmented with a persistent audit table writer.
type stdoutRecorder struct{}

func NewRecorder() Recorder { return &stdoutRecorder{} }

func (s *stdoutRecorder) Record(e Entry) {
	logger.Info("FundAudit",
		"trace=%s req=%s provider=%s op=%s dir=%s status=%d signOK=%v breaker=%v digest=%s dur=%dms err=%s biz=%s txn=%s ts=%s",
		e.TraceID, e.GatewayReqID, e.ProviderID, e.Operation, e.Direction,
		e.HTTPStatus, e.SignatureValid, e.CircuitOpen, e.PayloadDigest,
		e.DurationMs, e.ErrorCode, e.BusinessKey, e.ProviderTxnNo,
		time.Now().UTC().Format(time.RFC3339Nano),
	)
}

// Digest computes a SHA-256 fingerprint of a raw payload so we can reference
// it in audit logs and idempotency keys without ever persisting plaintext.
func Digest(raw []byte) string {
	sum := sha256.Sum256(raw)
	return hex.EncodeToString(sum[:])
}
