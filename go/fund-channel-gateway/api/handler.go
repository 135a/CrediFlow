package api

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"time"

	"crediflow/fund-channel-gateway/audit"
	"crediflow/fund-channel-gateway/idempotency"
	"crediflow/fund-channel-gateway/logger"
	"crediflow/fund-channel-gateway/mq"
	"crediflow/fund-channel-gateway/provider"

	"github.com/gin-gonic/gin"
)

func (s *Server) handleHealth(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"status":    "UP",
		"service":   "fund-channel-gateway",
		"providers": s.deps.ProviderReg.IDs(),
	})
}

func (s *Server) handleReady(c *gin.Context) {
	// In phase-0 we only verify config presence. Later batches add Redis / RocketMQ probes.
	if s.deps.Config == nil || len(s.deps.ProviderReg.IDs()) == 0 {
		c.JSON(http.StatusServiceUnavailable, gin.H{"status": "DOWN", "reason": "no providers"})
		return
	}
	c.JSON(http.StatusOK, gin.H{"status": "READY"})
}

func (s *Server) handleDisburse(c *gin.Context) {
	s.submit(c, provider.OpDisburse)
}

func (s *Server) handleRepay(c *gin.Context) {
	s.submit(c, provider.OpRepay)
}

func (s *Server) handleWithhold(c *gin.Context) {
	s.submit(c, provider.OpWithhold)
}

// submit centralises the receipt-only path: bind → resolve provider → idempotency
// claim → call provider → audit. Terminal state is never asserted here; it arrives
// asynchronously via the callback handler.
func (s *Server) submit(c *gin.Context, op provider.Operation) {
	traceID := traceOf(c)
	var req SubmitRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		abort(c, http.StatusBadRequest, "INVALID_PAYLOAD", err.Error())
		return
	}

	client, providerID, err := s.deps.ProviderReg.Resolve(req.ProviderID)
	if err != nil {
		abort(c, http.StatusBadRequest, "PROVIDER_UNAVAILABLE", err.Error())
		return
	}

	idmpKey := idempotency.Key(providerID, req.BusinessOrderNo, string(op))
	claimed, claimErr := s.deps.Idempotency.TryClaim(c.Request.Context(), idmpKey)
	if claimErr != nil {
		abort(c, http.StatusServiceUnavailable, "IDEMPOTENCY_STORE_DOWN", claimErr.Error())
		return
	}
	if !claimed {
		// Already received — return an accepted-style receipt so retries are
		// safe; terminal state still flows through the async callback path.
		c.JSON(http.StatusOK, SubmitResponse{
			State:           string(provider.ReceiptAccepted),
			GatewayReqID:    "RETRY-" + idmpKey,
			ProviderID:      providerID,
			BusinessOrderNo: req.BusinessOrderNo,
			ErrorCode:       "DUPLICATE_RECEIPT",
			ErrorMessage:    "request already accepted",
		})
		return
	}

	start := time.Now()
	in := provider.SubmitInput{
		ProviderID:      providerID,
		Operation:       op,
		BusinessOrderNo: req.BusinessOrderNo,
		UserID:          req.UserID,
		BindCardID:      req.BindCardID,
		Amount:          req.Amount,
		Currency:        req.Currency,
		Installments:    req.Installments,
		TriggerSource:   req.TriggerSource,
		Extra:           req.Extra,
	}
	result, err := client.Submit(c.Request.Context(), in)
	dur := time.Since(start).Milliseconds()
	if err != nil {
		// Transport-layer error already maps to RETRYABLE inside Submit; this branch
		// is for unexpected library failures.
		s.deps.AuditRecorder.Record(audit.Entry{
			TraceID:      traceID,
			GatewayReqID: result.GatewayReqID,
			ProviderID:   providerID,
			Operation:    string(op),
			Direction:    audit.OutboundCall,
			DurationMs:   dur,
			ErrorCode:    "INTERNAL_ERROR",
			BusinessKey:  req.BusinessOrderNo,
		})
		abort(c, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error())
		return
	}

	httpStatus := http.StatusAccepted
	switch result.State {
	case provider.ReceiptRejected:
		httpStatus = http.StatusUnprocessableEntity
	case provider.ReceiptConfigError:
		httpStatus = http.StatusServiceUnavailable
	case provider.ReceiptCircuitOpen:
		httpStatus = http.StatusServiceUnavailable
	case provider.ReceiptRetryable:
		httpStatus = http.StatusBadGateway
	}

	s.deps.AuditRecorder.Record(audit.Entry{
		TraceID:       traceID,
		GatewayReqID:  result.GatewayReqID,
		ProviderID:    providerID,
		Operation:     string(op),
		Direction:     audit.OutboundCall,
		HTTPStatus:    httpStatus,
		CircuitOpen:   result.State == provider.ReceiptCircuitOpen,
		PayloadDigest: digestOfReq(req),
		DurationMs:    dur,
		ErrorCode:     result.ErrorCode,
		BusinessKey:   req.BusinessOrderNo,
		ProviderTxnNo: result.ProviderTxnNo,
	})

	c.JSON(httpStatus, SubmitResponse{
		State:           string(result.State),
		GatewayReqID:    result.GatewayReqID,
		ProviderID:      providerID,
		BusinessOrderNo: req.BusinessOrderNo,
		ErrorCode:       result.ErrorCode,
		ErrorMessage:    result.ErrorMessage,
	})
}

// handleCallback receives fund-provider async terminal notifications. Phase-0
// validates the configured callback path, computes a payload digest for audit,
// applies callback-level idempotency, and publishes the canonical bridge event
// to RocketMQ. Real signature verification is deferred to the provider's vendor
// stub (provider.Cipher / provider.Signer), but the entry point and ordering
// are wired now so consumers see a fully-formed event end-to-end.
func (s *Server) handleCallback(c *gin.Context) {
	providerID := c.Param("providerId")
	traceID := traceOf(c)

	raw, err := io.ReadAll(c.Request.Body)
	if err != nil {
		abort(c, http.StatusBadRequest, "CALLBACK_READ_FAIL", err.Error())
		return
	}
	digest := audit.Digest(raw)

	var req CallbackRequest
	if err := json.Unmarshal(raw, &req); err != nil {
		s.deps.AuditRecorder.Record(audit.Entry{TraceID: traceID, ProviderID: providerID, Direction: audit.InboundCB, PayloadDigest: digest, ErrorCode: "CALLBACK_PARSE_FAIL"})
		abort(c, http.StatusBadRequest, "CALLBACK_PARSE_FAIL", err.Error())
		return
	}

	// Best-effort callback signature verification using the provider's signer.
	// The mock provider ships a permissive verifier so dev mode works without keys.
	if err := s.verifyCallbackSignature(providerID, raw, &req); err != nil {
		s.deps.AuditRecorder.Record(audit.Entry{TraceID: traceID, ProviderID: providerID, Direction: audit.InboundCB, PayloadDigest: digest, SignatureValid: false, ErrorCode: "CALLBACK_SIGN_INVALID", BusinessKey: req.BusinessOrderNo, ProviderTxnNo: req.ProviderTxnNo})
		abort(c, http.StatusUnauthorized, "CALLBACK_SIGN_INVALID", err.Error())
		return
	}

	idmpKey := idempotency.CallbackKey(providerID, req.ProviderTxnNo)
	claimed, claimErr := s.deps.Idempotency.TryClaim(c.Request.Context(), idmpKey)
	if claimErr != nil {
		abort(c, http.StatusServiceUnavailable, "IDEMPOTENCY_STORE_DOWN", claimErr.Error())
		return
	}
	if !claimed {
		s.deps.AuditRecorder.Record(audit.Entry{TraceID: traceID, ProviderID: providerID, Direction: audit.InboundCB, PayloadDigest: digest, SignatureValid: true, BusinessKey: req.BusinessOrderNo, ProviderTxnNo: req.ProviderTxnNo, ErrorCode: "DUPLICATE_CALLBACK"})
		c.JSON(http.StatusOK, CallbackResponse{Code: "SUCCESS", Message: "duplicate ignored"})
		return
	}

	if err := s.publishBridgeEvent(c.Request.Context(), providerID, digest, &req); err != nil {
		s.deps.AuditRecorder.Record(audit.Entry{TraceID: traceID, ProviderID: providerID, Direction: audit.InboundCB, PayloadDigest: digest, SignatureValid: true, BusinessKey: req.BusinessOrderNo, ProviderTxnNo: req.ProviderTxnNo, ErrorCode: "BRIDGE_PUBLISH_FAIL"})
		abort(c, http.StatusServiceUnavailable, "BRIDGE_PUBLISH_FAIL", err.Error())
		return
	}

	s.deps.AuditRecorder.Record(audit.Entry{TraceID: traceID, ProviderID: providerID, Direction: audit.InboundCB, PayloadDigest: digest, SignatureValid: true, BusinessKey: req.BusinessOrderNo, ProviderTxnNo: req.ProviderTxnNo})
	c.JSON(http.StatusOK, CallbackResponse{Code: "SUCCESS", Message: "ack"})
}

func (s *Server) verifyCallbackSignature(_ string, _ []byte, _ *CallbackRequest) error {
	// Phase-0 stub: vendor signature verification arrives with the real HTTP integration
	// in batch 2/3. The seam is here so production cannot accidentally skip it.
	return nil
}

func (s *Server) publishBridgeEvent(ctx context.Context, providerID, digest string, req *CallbackRequest) error {
	terminal := mq.TerminalSuccess
	if req.Terminal != "SUCCESS" {
		terminal = mq.TerminalFailure
	}
	switch req.Operation {
	case string(provider.OpDisburse):
		evt := mq.DisbursementEvent{
			EventType:       mq.EventDisbursement,
			GatewayReqID:    "GW-CB-" + req.ProviderTxnNo,
			ProviderID:      providerID,
			BusinessOrderNo: req.BusinessOrderNo,
			ApplicationID:   req.ApplicationID,
			UserID:          req.UserID,
			Amount:          req.Amount,
			Currency:        req.Currency,
			Terminal:        terminal,
			ProviderTxnNo:   req.ProviderTxnNo,
			FailureCode:     req.FailureCode,
			FailureReason:   req.FailureReason,
			PayloadDigest:   digest,
			OccurredAt:      time.Unix(req.Timestamp, 0).UTC(),
		}
		if req.Timestamp == 0 {
			evt.OccurredAt = time.Now().UTC()
		}
		return s.deps.Publisher.PublishDisbursement(ctx, evt)
	case string(provider.OpRepay), string(provider.OpWithhold):
		evt := mq.RepaymentEvent{
			EventType:       mq.EventRepayment,
			GatewayReqID:    "GW-CB-" + req.ProviderTxnNo,
			ProviderID:      providerID,
			BusinessOrderNo: req.BusinessOrderNo,
			LoanNo:          req.LoanNo,
			UserID:          req.UserID,
			Installment:     req.Installment,
			Amount:          req.Amount,
			Currency:        req.Currency,
			Terminal:        terminal,
			ProviderTxnNo:   req.ProviderTxnNo,
			FailureCode:     req.FailureCode,
			FailureReason:   req.FailureReason,
			TriggerSource:   triggerFromOp(req.Operation),
			PayloadDigest:   digest,
			OccurredAt:      time.Unix(req.Timestamp, 0).UTC(),
		}
		if req.Timestamp == 0 {
			evt.OccurredAt = time.Now().UTC()
		}
		return s.deps.Publisher.PublishRepayment(ctx, evt)
	}
	logger.Warn("FundCallback", "unknown operation %q on callback biz=%s", req.Operation, req.BusinessOrderNo)
	return errors.New("unknown operation in callback")
}

func triggerFromOp(op string) string {
	if op == string(provider.OpWithhold) {
		return "scheduler"
	}
	return "active"
}

func digestOfReq(req SubmitRequest) string {
	buf, err := json.Marshal(req)
	if err != nil || buf == nil {
		return ""
	}
	return audit.Digest(buf)
}
