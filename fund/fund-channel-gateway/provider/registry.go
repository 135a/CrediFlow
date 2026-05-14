package provider

import (
	"crediflow/fund-channel-gateway/config"
	"crediflow/fund-channel-gateway/logger"
)

// Registry holds an instantiated client per enabled provider plus the default
// fallback used by hybrid routing (decision #2). Construction failures are logged
// but never panic — the gateway still serves other providers.
type Registry struct {
	clients   map[string]FundProviderClient
	defaultID string
}

func NewRegistry(cfg *config.Config) *Registry {
	r := &Registry{
		clients:   make(map[string]FundProviderClient),
		defaultID: cfg.DefaultProviderID,
	}
	for id, p := range cfg.Providers {
		if !p.Enabled {
			continue
		}
		var client FundProviderClient
		if p.UseMock {
			client = NewMockProvider(id, p.MockAsyncDelayMs)
			logger.Info("FundProvider", "provider=%s registered as MOCK", id)
		} else {
			client = NewHTTPProvider(id, p)
			logger.Info("FundProvider", "provider=%s registered as HTTP baseUrl=%s", id, p.BaseURL)
		}
		// Sentinel breaker registration. Failure is logged but non-fatal so dev runs
		// without the sentinel control plane still work.
		if err := registerBreaker(id, p.CircuitBreaker); err != nil {
			logger.Warn("FundProvider", "sentinel breaker init failed for %s: %v", id, err)
		}
		r.clients[id] = client
	}
	return r
}

// Resolve implements the hybrid routing rule: explicit providerId from Java wins;
// blank / unknown falls back to the configured default.
func (r *Registry) Resolve(providerID string) (FundProviderClient, string, error) {
	if providerID != "" {
		if c, ok := r.clients[providerID]; ok {
			return c, providerID, nil
		}
		// Explicit but unknown — refuse rather than silently substitute.
		return nil, providerID, ErrUnknownProvider
	}
	if c, ok := r.clients[r.defaultID]; ok {
		return c, r.defaultID, nil
	}
	return nil, "", ErrUnknownProvider
}

// IDs is for diagnostics (e.g. /health response).
func (r *Registry) IDs() []string {
	out := make([]string, 0, len(r.clients))
	for id := range r.clients {
		out = append(out, id)
	}
	return out
}
