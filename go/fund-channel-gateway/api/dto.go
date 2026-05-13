package api

// SubmitRequest is the common shape Java fund-flow-service and repayment-service
// send to the gateway. ProviderID is optional — when empty the gateway falls back
// to the Nacos defaultProviderId (hybrid routing, decision #2). BindCardID is the
// token-ised reference to the user's bank card (decision #3): Java MUST NOT
// transport raw card numbers across the internal boundary.
type SubmitRequest struct {
	ProviderID      string            `json:"providerId,omitempty"`
	BusinessOrderNo string            `json:"businessOrderNo" binding:"required"`
	UserID          string            `json:"userId" binding:"required"`
	BindCardID      string            `json:"bindCardId" binding:"required"`
	Amount          string            `json:"amount" binding:"required"`
	Currency        string            `json:"currency,omitempty"`
	Installments    int               `json:"installments,omitempty"`
	TriggerSource   string            `json:"triggerSource,omitempty"`
	Extra           map[string]string `json:"extra,omitempty"`
}

// SubmitResponse is the synchronous receipt. Terminal state always arrives via
// the async callback path (RocketMQ). Receipt states map 1:1 to provider.ReceiptState.
type SubmitResponse struct {
	State           string `json:"state"`
	GatewayReqID    string `json:"gatewayRequestId"`
	ProviderID      string `json:"providerId"`
	BusinessOrderNo string `json:"businessOrderNo"`
	ErrorCode       string `json:"errorCode,omitempty"`
	ErrorMessage    string `json:"errorMessage,omitempty"`
}

// CallbackRequest is what fund providers POST to the gateway's public callback URL
// (one URL per providerId). The shape is intentionally permissive in phase-0 — real
// providers seldom share a schema; the gateway re-canonicalises into the bridge
// event before pushing to RocketMQ.
type CallbackRequest struct {
	BusinessOrderNo string            `json:"businessOrderNo"`
	ProviderTxnNo   string            `json:"providerTxnNo"`
	Operation       string            `json:"operation"` // DISBURSE | REPAY | WITHHOLD
	Terminal        string            `json:"terminal"`  // SUCCESS | FAILED
	Amount          string            `json:"amount"`
	Currency        string            `json:"currency,omitempty"`
	FailureCode     string            `json:"failureCode,omitempty"`
	FailureReason   string            `json:"failureReason,omitempty"`
	UserID          string            `json:"userId,omitempty"`
	LoanNo          string            `json:"loanNo,omitempty"`
	ApplicationID   string            `json:"applicationId,omitempty"`
	Installment     int               `json:"installment,omitempty"`
	Sign            string            `json:"sign"`
	Timestamp       int64             `json:"timestamp"`
	Extra           map[string]string `json:"extra,omitempty"`
}

type CallbackResponse struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type ErrorResponse struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	TraceID string `json:"traceId,omitempty"`
}
