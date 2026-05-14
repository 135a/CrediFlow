package mq

import "time"

// EventType captures the bridge channel category, mirroring existing Java consumer
// names so consumers do not need to be retrofitted in phase 0.
type EventType string

const (
	EventDisbursement EventType = "FUND_DISBURSED_EVENT"
	EventRepayment    EventType = "REPAYMENT_SETTLED_EVENT"
)

type Terminal string

const (
	TerminalSuccess Terminal = "SUCCESS"
	TerminalFailure Terminal = "FAILED"
)

// DisbursementEvent is the body Java fund-flow-service consumers receive when a
// disbursement reaches a terminal state at the fund provider side.
type DisbursementEvent struct {
	EventType       EventType `json:"eventType"`
	GatewayReqID    string    `json:"gatewayRequestId"`
	ProviderID      string    `json:"providerId"`
	BusinessOrderNo string    `json:"businessOrderNo"`
	ApplicationID   string    `json:"applicationId,omitempty"`
	UserID          string    `json:"userId,omitempty"`
	Amount          string    `json:"amount"`
	Currency        string    `json:"currency"`
	Terminal        Terminal  `json:"terminal"`
	ProviderTxnNo   string    `json:"providerTxnNo,omitempty"`
	FailureCode     string    `json:"failureCode,omitempty"`
	FailureReason   string    `json:"failureReason,omitempty"`
	PayloadDigest   string    `json:"payloadDigest"`
	OccurredAt      time.Time `json:"occurredAt"`
}

// RepaymentEvent is consumed by Java repayment-service / post-loan-service to settle
// installments and update profile labels. Covers both proactive and scheduled deduct.
type RepaymentEvent struct {
	EventType       EventType `json:"eventType"`
	GatewayReqID    string    `json:"gatewayRequestId"`
	ProviderID      string    `json:"providerId"`
	BusinessOrderNo string    `json:"businessOrderNo"`
	LoanNo          string    `json:"loanNo,omitempty"`
	UserID          string    `json:"userId,omitempty"`
	Installment     int       `json:"installment,omitempty"`
	Amount          string    `json:"amount"`
	Currency        string    `json:"currency"`
	Terminal        Terminal  `json:"terminal"`
	ProviderTxnNo   string    `json:"providerTxnNo,omitempty"`
	FailureCode     string    `json:"failureCode,omitempty"`
	FailureReason   string    `json:"failureReason,omitempty"`
	TriggerSource   string    `json:"triggerSource,omitempty"` // active|scheduler
	PayloadDigest   string    `json:"payloadDigest"`
	OccurredAt      time.Time `json:"occurredAt"`
}
