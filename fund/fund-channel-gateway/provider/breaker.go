package provider

import (
	"errors"

	"crediflow/fund-channel-gateway/config"
	"crediflow/fund-channel-gateway/logger"

	sentinel "github.com/alibaba/sentinel-golang/api"
	"github.com/alibaba/sentinel-golang/core/base"
	"github.com/alibaba/sentinel-golang/core/circuitbreaker"
)

var (
	sentinelReady   bool
	errProviderCall = errors.New("provider call failed")
)

// InitBreaker bootstraps sentinel-golang exactly once. Failure to initialise
// degrades to a "always allow" behaviour so dev environments without a sentinel
// dashboard still work. Production startup validation should require it later.
func InitBreaker() error {
	if sentinelReady {
		return nil
	}
	if err := sentinel.InitDefault(); err != nil {
		return err
	}
	sentinelReady = true
	logger.Info("FundProvider", "sentinel-golang initialised")
	return nil
}

// resource name convention: fund-provider:<id>
func resourceName(id string) string { return "fund-provider:" + id }

// registerBreaker installs an error-ratio breaker for the given provider id.
// Called from Registry while iterating Nacos providers.
func registerBreaker(id string, cb config.CircuitConfig) error {
	if err := InitBreaker(); err != nil {
		return err
	}
	rules := []*circuitbreaker.Rule{
		{
			Resource:                     resourceName(id),
			Strategy:                     circuitbreaker.ErrorRatio,
			RetryTimeoutMs:               cb.RetryTimeoutMs,
			MinRequestAmount:             cb.MinRequestAmount,
			StatIntervalMs:               cb.StatIntervalMs,
			StatSlidingWindowBucketCount: 10,
			Threshold:                    cb.ErrorRatioThreshold,
		},
	}
	_, err := circuitbreaker.LoadRules(rules)
	return err
}

// AllowBreaker checks whether the breaker for a provider permits a new call.
// Returns (true, entry) when permitted; the entry must be passed to either
// SuccessBreaker or FailBreaker to keep statistics accurate.
func AllowBreaker(id string) (bool, *base.SentinelEntry) {
	if !sentinelReady {
		return true, nil
	}
	entry, blockErr := sentinel.Entry(resourceName(id))
	if blockErr != nil {
		logger.Warn("FundProvider", "breaker open for %s: %s", id, blockErr.BlockMsg())
		return false, nil
	}
	return true, entry
}

func SuccessBreaker(id string) {
	_ = id // statistics are recorded via entry.Exit by the caller, kept for API symmetry
}

// FailBreaker feeds a single failure into sentinel's error-ratio statistics for
// the given provider so the breaker can open under sustained downstream errors.
// Use only when no live entry is on hand; otherwise prefer TraceFailure.
func FailBreaker(id string) {
	if !sentinelReady {
		return
	}
	entry, blockErr := sentinel.Entry(resourceName(id))
	if blockErr != nil {
		return
	}
	sentinel.TraceError(entry, errProviderCall)
	entry.Exit()
}

// TraceFailure marks the supplied entry as failed without performing another
// Entry call, preserving the original allow-list span and statistics.
func TraceFailure(entry *base.SentinelEntry) {
	if !sentinelReady || entry == nil {
		return
	}
	sentinel.TraceError(entry, errProviderCall)
}
