package provider

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"time"

	"crediflow/fund-channel-gateway/config"
	"crediflow/fund-channel-gateway/logger"

	"github.com/google/uuid"
)

// HTTPProvider is the real-vendor stub. Phase-0 keeps the network call shape and
// the resilience knobs but routes outgoing payloads through Signer / Cipher hooks
// whose actual algorithms will be supplied per-vendor in later batches. The point
// of the stub is to make sure the seam exists so that batches 2/3 can drop in a
// vendor without touching gateway business logic.
type HTTPProvider struct {
	id     string
	cfg    config.Provider
	client *http.Client
	signer Signer
	cipher Cipher
}

func NewHTTPProvider(id string, p config.Provider) *HTTPProvider {
	timeout := time.Duration(p.HTTPTimeoutMs) * time.Millisecond
	return &HTTPProvider{
		id:     id,
		cfg:    p,
		client: &http.Client{Timeout: timeout},
		signer: NewSigner(p.SignAlgorithm, p.AppSecret),
		cipher: NewCipher(p.AESKey, p.RSAPublicKey, p.RSAPrivateKey, p.EncryptFields),
	}
}

func (h *HTTPProvider) ID() string { return h.id }

func (h *HTTPProvider) Submit(ctx context.Context, in SubmitInput) (SubmitResult, error) {
	reqID := "GW-" + uuid.NewString()

	allowed, entry := AllowBreaker(h.id)
	if !allowed {
		return SubmitResult{State: ReceiptCircuitOpen, GatewayReqID: reqID, ErrorCode: "CIRCUIT_OPEN"}, nil
	}
	if entry != nil {
		defer entry.Exit()
	}

	payload, err := h.cipher.EncryptFields(map[string]any{
		"appKey":          h.cfg.AppKey,
		"businessOrderNo": in.BusinessOrderNo,
		"userId":          in.UserID,
		"bindCardId":      in.BindCardID,
		"amount":          in.Amount,
		"currency":        in.Currency,
		"installments":    in.Installments,
		"operation":       string(in.Operation),
		"triggerSource":   in.TriggerSource,
		"gatewayReqId":    reqID,
		"timestamp":       time.Now().UTC().Unix(),
	})
	if err != nil {
		return SubmitResult{State: ReceiptConfigError, GatewayReqID: reqID, ErrorCode: "ENCRYPT_FAIL", ErrorMessage: err.Error()}, nil
	}
	signed, err := h.signer.Sign(payload)
	if err != nil {
		return SubmitResult{State: ReceiptConfigError, GatewayReqID: reqID, ErrorCode: "SIGN_FAIL", ErrorMessage: err.Error()}, nil
	}

	body, err := json.Marshal(signed)
	if err != nil {
		return SubmitResult{State: ReceiptConfigError, GatewayReqID: reqID, ErrorCode: "MARSHAL_FAIL", ErrorMessage: err.Error()}, nil
	}

	endpoint, err := h.endpointFor(in.Operation)
	if err != nil {
		return SubmitResult{State: ReceiptConfigError, GatewayReqID: reqID, ErrorCode: "ROUTE_UNDEFINED", ErrorMessage: err.Error()}, nil
	}

	res, attemptErr := h.callWithRetry(ctx, endpoint, body)
	if attemptErr != nil {
		// Mark the in-flight entry as failed so the error-ratio stat advances.
		if entry != nil {
			TraceFailure(entry)
		} else {
			FailBreaker(h.id)
		}
		return SubmitResult{State: ReceiptRetryable, GatewayReqID: reqID, ErrorCode: "PROVIDER_UNREACHABLE", ErrorMessage: attemptErr.Error()}, nil
	}
	SuccessBreaker(h.id)

	verified, verr := h.signer.Verify(res)
	if verr != nil {
		return SubmitResult{State: ReceiptRejected, GatewayReqID: reqID, ErrorCode: "RESPONSE_SIGN_INVALID", ErrorMessage: verr.Error()}, nil
	}

	if rejected, code, msg := isProviderReject(verified); rejected {
		return SubmitResult{State: ReceiptRejected, GatewayReqID: reqID, ErrorCode: code, ErrorMessage: msg}, nil
	}

	providerTxn, _ := verified["providerTxnNo"].(string)
	return SubmitResult{State: ReceiptAccepted, GatewayReqID: reqID, ProviderTxnNo: providerTxn}, nil
}

func (h *HTTPProvider) endpointFor(op Operation) (string, error) {
	// Vendor-specific path mapping arrives in batch 2; phase-0 wires a stable convention.
	switch op {
	case OpDisburse:
		return h.cfg.BaseURL + "/v1/disburse", nil
	case OpRepay:
		return h.cfg.BaseURL + "/v1/repay", nil
	case OpWithhold:
		return h.cfg.BaseURL + "/v1/withhold", nil
	}
	return "", fmt.Errorf("unknown operation: %s", op)
}

func (h *HTTPProvider) callWithRetry(ctx context.Context, url string, body []byte) (map[string]any, error) {
	maxRetries := h.cfg.MaxRetries
	if maxRetries < 0 {
		maxRetries = 0
	}
	backoff := time.Duration(h.cfg.RetryBackoffMs) * time.Millisecond

	var lastErr error
	for attempt := 0; attempt <= maxRetries; attempt++ {
		if attempt > 0 {
			time.Sleep(backoff * time.Duration(attempt))
		}
		req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
		if err != nil {
			return nil, fmt.Errorf("build request: %w", err)
		}
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("X-App-Key", h.cfg.AppKey)
		resp, err := h.client.Do(req)
		if err != nil {
			lastErr = err
			logger.Warn("FundProvider", "%s attempt %d/%d transport error: %v", h.id, attempt+1, maxRetries+1, err)
			continue
		}
		raw, readErr := io.ReadAll(resp.Body)
		_ = resp.Body.Close()
		if readErr != nil {
			lastErr = readErr
			continue
		}
		if resp.StatusCode >= 500 {
			lastErr = fmt.Errorf("provider returned %d", resp.StatusCode)
			continue
		}
		var parsed map[string]any
		if err := json.Unmarshal(raw, &parsed); err != nil {
			return nil, fmt.Errorf("decode provider response: %w", err)
		}
		return parsed, nil
	}
	if lastErr == nil {
		lastErr = errors.New("retries exhausted")
	}
	return nil, lastErr
}

func isProviderReject(payload map[string]any) (bool, string, string) {
	codeAny, ok := payload["code"]
	if !ok {
		return false, "", ""
	}
	code := fmt.Sprintf("%v", codeAny)
	if code == "" || code == "0" || code == "SUCCESS" || code == "00000" {
		return false, "", ""
	}
	msg, _ := payload["message"].(string)
	return true, code, msg
}
