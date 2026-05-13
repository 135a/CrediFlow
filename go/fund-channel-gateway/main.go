package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"crediflow/fund-channel-gateway/api"
	"crediflow/fund-channel-gateway/audit"
	"crediflow/fund-channel-gateway/config"
	"crediflow/fund-channel-gateway/idempotency"
	"crediflow/fund-channel-gateway/logger"
	"crediflow/fund-channel-gateway/mq"
	"crediflow/fund-channel-gateway/provider"
)

func main() {
	logger.Init()
	logger.Info("FundGateway", "starting fund-channel-gateway...")

	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("[FundGateway] FATAL config load failed: %v", err)
	}
	if err := config.Validate(cfg); err != nil {
		log.Fatalf("[FundGateway] FATAL config validation failed: %v", err)
	}
	logger.Info("FundGateway", "env=%s providers=%d default=%s addr=%s",
		cfg.Env, len(cfg.Providers), cfg.DefaultProviderID, cfg.HTTP.Addr)

	idempotency.Init(cfg.Redis)
	publisher := mq.NewPublisher(cfg.RocketMQ)
	defer publisher.Close()

	registry := provider.NewRegistry(cfg)

	deps := &api.Deps{
		Config:        cfg,
		Idempotency:   idempotency.Default(),
		Publisher:     publisher,
		ProviderReg:   registry,
		AuditRecorder: audit.NewRecorder(),
	}

	// Wire mock providers so their simulated async callbacks publish the same
	// bridge events real callbacks would, exercising the full Java consumer path
	// during dev/test runs without a vendor or a vendor-style HTTP self-call.
	wireMockCallbacks(registry, publisher, deps.AuditRecorder, cfg)

	srv := api.NewServer(deps)

	httpSrv := &http.Server{
		Addr:         cfg.HTTP.Addr,
		Handler:      srv,
		ReadTimeout:  time.Duration(cfg.HTTP.ReadTimeoutMs) * time.Millisecond,
		WriteTimeout: time.Duration(cfg.HTTP.WriteTimeoutMs) * time.Millisecond,
	}

	go func() {
		logger.Info("FundGateway", "HTTP server listening on %s", cfg.HTTP.Addr)
		if err := httpSrv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("[FundGateway] FATAL HTTP server error: %v", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	logger.Info("FundGateway", "shutdown signal received")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = httpSrv.Shutdown(ctx)
	logger.Info("FundGateway", "exited gracefully")
}

// wireMockCallbacks lets dev/test runs exercise the full callback → MQ path even
// without a vendor by piggy-backing on the mock provider's async hook.
func wireMockCallbacks(reg *provider.Registry, publisher mq.Publisher, recorder audit.Recorder, cfg *config.Config) {
	for _, id := range reg.IDs() {
		p := cfg.Providers[id]
		if !p.UseMock {
			continue
		}
		client, _, err := reg.Resolve(id)
		if err != nil {
			continue
		}
		mock, ok := client.(*provider.MockProvider)
		if !ok {
			continue
		}
		hook := buildMockHook(publisher, recorder)
		mock.WireCallbackHook(hook)
	}
}

func buildMockHook(publisher mq.Publisher, recorder audit.Recorder) provider.CallbackHook {
	return func(providerID string, in provider.SubmitInput, gatewayReqID string) {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		now := time.Now().UTC()
		digest := "mock-" + gatewayReqID
		switch in.Operation {
		case provider.OpDisburse:
			evt := mq.DisbursementEvent{
				EventType:       mq.EventDisbursement,
				GatewayReqID:    gatewayReqID,
				ProviderID:      providerID,
				BusinessOrderNo: in.BusinessOrderNo,
				UserID:          in.UserID,
				Amount:          in.Amount,
				Currency:        in.Currency,
				Terminal:        mq.TerminalSuccess,
				ProviderTxnNo:   "MOCK-TXN-" + in.BusinessOrderNo,
				PayloadDigest:   digest,
				OccurredAt:      now,
			}
			if err := publisher.PublishDisbursement(ctx, evt); err != nil {
				logger.Error("FundCallback", "mock publish disbursement failed: %v", err)
			}
		case provider.OpRepay, provider.OpWithhold:
			trigger := "active"
			if in.Operation == provider.OpWithhold {
				trigger = "scheduler"
			}
			evt := mq.RepaymentEvent{
				EventType:       mq.EventRepayment,
				GatewayReqID:    gatewayReqID,
				ProviderID:      providerID,
				BusinessOrderNo: in.BusinessOrderNo,
				UserID:          in.UserID,
				Installment:     in.Installments,
				Amount:          in.Amount,
				Currency:        in.Currency,
				Terminal:        mq.TerminalSuccess,
				ProviderTxnNo:   "MOCK-TXN-" + in.BusinessOrderNo,
				TriggerSource:   trigger,
				PayloadDigest:   digest,
				OccurredAt:      now,
			}
			if err := publisher.PublishRepayment(ctx, evt); err != nil {
				logger.Error("FundCallback", "mock publish repayment failed: %v", err)
			}
		}
		recorder.Record(audit.Entry{
			TraceID:        "mock",
			GatewayReqID:   gatewayReqID,
			ProviderID:     providerID,
			Operation:      string(in.Operation),
			Direction:      audit.InboundCB,
			SignatureValid: true,
			BusinessKey:    in.BusinessOrderNo,
			ProviderTxnNo:  "MOCK-TXN-" + in.BusinessOrderNo,
		})
	}
}
