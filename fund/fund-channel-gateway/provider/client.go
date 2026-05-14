package provider

import (
	"context"
	"errors"
)

// Operation enumerates fund operations the gateway can submit to providers.
// Naming is consistent across the receipt API, the FundProviderClient call,
// and audit/idempotency keys so traces stay aligned.
type Operation string

const (
	OpDisburse Operation = "DISBURSE"
	OpRepay    Operation = "REPAY"
	OpWithhold Operation = "WITHHOLD"
)

// ReceiptState models the synchronous outcome layer the gateway returns to Java.
// Terminal results NEVER use AcceptedSync — they only arrive through async callback.
type ReceiptState string

const (
	ReceiptAccepted    ReceiptState = "ACCEPTED"
	ReceiptRejected    ReceiptState = "REJECTED"     // provider explicitly refused (4xx-equivalent)
	ReceiptRetryable   ReceiptState = "RETRYABLE"    // network/5xx; gateway already retried, exhausted
	ReceiptCircuitOpen ReceiptState = "CIRCUIT_OPEN" // sentinel breaker rejected
	ReceiptConfigError ReceiptState = "CONFIG_ERROR" // missing Nacos config — fail fast
)

// SubmitInput is the canonical, provider-agnostic command. Sensitive bank-card
// data is referenced via BindCardID only (decision #3 token-isation).
type SubmitInput struct {
	ProviderID      string
	Operation       Operation
	BusinessOrderNo string
	UserID          string
	BindCardID      string
	Amount          string // decimal string to avoid float loss
	Currency        string
	Installments    int
	TriggerSource   string // active|scheduler|disburse-chain
	Extra           map[string]string
}

// SubmitResult is the synchronous receipt the gateway returns to Java.
type SubmitResult struct {
	State         ReceiptState
	GatewayReqID  string
	ProviderTxnNo string // populated only when provider returns it synchronously (some APIs do)
	ErrorCode     string
	ErrorMessage  string
}

// FundProviderClient is the seam between gateway business logic and any specific
// fund provider integration. Phase-0 ships with a MockProvider and an HTTP stub
// that exposes the sign / encrypt extension points without locking to a vendor.
type FundProviderClient interface {
	ID() string
	Submit(ctx context.Context, in SubmitInput) (SubmitResult, error)
}

// ErrUnknownProvider is returned when Java passes a providerId that has no Nacos entry
// (or the entry is disabled). Hybrid routing falls back to default when Java omits it.
var ErrUnknownProvider = errors.New("unknown or disabled providerId")
