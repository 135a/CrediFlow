package provider

import (
	"context"
	"fmt"
	"sync"
	"time"

	"crediflow/fund-channel-gateway/logger"

	"github.com/google/uuid"
)

// MockProvider is the phase-0 deterministic provider for dev/test. It always
// returns ACCEPTED synchronously and, when CallbackHook is wired, simulates an
// asynchronous terminal callback after MockAsyncDelayMs so the full pipeline
// (receipt → idempotency → callback → MQ → Java) can be exercised end-to-end.
type MockProvider struct {
	id             string
	asyncDelay     time.Duration
	mu             sync.RWMutex
	callbackHookFn CallbackHook
}

// CallbackHook is implemented by the callback simulator wired in main; the mock
// invokes it asynchronously to model real provider behaviour.
type CallbackHook func(providerID string, in SubmitInput, gatewayReqID string)

func NewMockProvider(id string, asyncDelayMs int) *MockProvider {
	delay := time.Duration(asyncDelayMs) * time.Millisecond
	if delay <= 0 {
		delay = 2 * time.Second
	}
	return &MockProvider{id: id, asyncDelay: delay}
}

func (m *MockProvider) ID() string { return m.id }

func (m *MockProvider) WireCallbackHook(h CallbackHook) {
	m.mu.Lock()
	m.callbackHookFn = h
	m.mu.Unlock()
}

func (m *MockProvider) Submit(_ context.Context, in SubmitInput) (SubmitResult, error) {
	reqID := "GW-" + uuid.NewString()
	logger.Info("FundProvider", "MOCK %s op=%s biz=%s amount=%s currency=%s reqId=%s",
		m.id, in.Operation, in.BusinessOrderNo, in.Amount, in.Currency, reqID)

	m.mu.RLock()
	hook := m.callbackHookFn
	m.mu.RUnlock()
	if hook != nil {
		go func() {
			time.Sleep(m.asyncDelay)
			hook(m.id, in, reqID)
		}()
	}

	return SubmitResult{
		State:        ReceiptAccepted,
		GatewayReqID: reqID,
	}, nil
}

func (m *MockProvider) String() string { return fmt.Sprintf("MockProvider(%s)", m.id) }
