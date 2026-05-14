package mq

import (
	"context"
	"encoding/json"
	"fmt"

	"crediflow/fund-channel-gateway/config"
	"crediflow/fund-channel-gateway/logger"

	"github.com/apache/rocketmq-client-go/v2"
	"github.com/apache/rocketmq-client-go/v2/primitive"
	"github.com/apache/rocketmq-client-go/v2/producer"
)

// Publisher abstracts the bridge from gateway to Java consumers. Phase-0 default
// is RocketMQ (decision #1); when NameServer is empty we fall back to a noop
// publisher that only logs the payload — useful for local dev without an MQ
// broker. Production startup validation refuses to run without a NameServer.
type Publisher interface {
	PublishDisbursement(ctx context.Context, evt DisbursementEvent) error
	PublishRepayment(ctx context.Context, evt RepaymentEvent) error
	Close()
}

type noopPublisher struct{}

func (n *noopPublisher) PublishDisbursement(_ context.Context, evt DisbursementEvent) error {
	logger.Warn("FundMQ", "noop publisher: would emit %s for biz=%s terminal=%s", evt.EventType, evt.BusinessOrderNo, evt.Terminal)
	return nil
}

func (n *noopPublisher) PublishRepayment(_ context.Context, evt RepaymentEvent) error {
	logger.Warn("FundMQ", "noop publisher: would emit %s for biz=%s terminal=%s", evt.EventType, evt.BusinessOrderNo, evt.Terminal)
	return nil
}

func (n *noopPublisher) Close() {}

type rocketmqPublisher struct {
	prod          rocketmq.Producer
	disburseTopic string
	repayTopic    string
}

func NewPublisher(cfg config.RocketMQConfig) Publisher {
	if cfg.NameServer == "" {
		logger.Warn("FundMQ", "rocketmq.nameServer empty; using noop publisher (dev only)")
		return &noopPublisher{}
	}
	p, err := rocketmq.NewProducer(
		producer.WithNameServer([]string{cfg.NameServer}),
		producer.WithGroupName(cfg.GroupID),
		producer.WithRetry(2),
	)
	if err != nil {
		logger.Error("FundMQ", "rocketmq producer init failed (%v); using noop", err)
		return &noopPublisher{}
	}
	if err := p.Start(); err != nil {
		logger.Error("FundMQ", "rocketmq producer start failed (%v); using noop", err)
		return &noopPublisher{}
	}
	logger.Info("FundMQ", "rocketmq producer started nameServer=%s group=%s", cfg.NameServer, cfg.GroupID)
	return &rocketmqPublisher{
		prod:          p,
		disburseTopic: cfg.DisburseTopic,
		repayTopic:    cfg.RepayTopic,
	}
}

func (r *rocketmqPublisher) PublishDisbursement(ctx context.Context, evt DisbursementEvent) error {
	body, err := json.Marshal(evt)
	if err != nil {
		return fmt.Errorf("marshal disbursement event: %w", err)
	}
	msg := primitive.NewMessage(r.disburseTopic, body)
	msg.WithKeys([]string{evt.BusinessOrderNo})
	res, err := r.prod.SendSync(ctx, msg)
	if err != nil {
		return fmt.Errorf("rocketmq send disbursement: %w", err)
	}
	logger.Info("FundMQ", "sent %s biz=%s msgId=%s", evt.EventType, evt.BusinessOrderNo, res.MsgID)
	return nil
}

func (r *rocketmqPublisher) PublishRepayment(ctx context.Context, evt RepaymentEvent) error {
	body, err := json.Marshal(evt)
	if err != nil {
		return fmt.Errorf("marshal repayment event: %w", err)
	}
	msg := primitive.NewMessage(r.repayTopic, body)
	msg.WithKeys([]string{evt.BusinessOrderNo})
	res, err := r.prod.SendSync(ctx, msg)
	if err != nil {
		return fmt.Errorf("rocketmq send repayment: %w", err)
	}
	logger.Info("FundMQ", "sent %s biz=%s msgId=%s", evt.EventType, evt.BusinessOrderNo, res.MsgID)
	return nil
}

func (r *rocketmqPublisher) Close() {
	if r.prod != nil {
		_ = r.prod.Shutdown()
	}
}
