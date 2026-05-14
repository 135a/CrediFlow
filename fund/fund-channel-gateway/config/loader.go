package config

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

// Load resolves config from Nacos when FUND_GATEWAY_NACOS_SERVER is set, otherwise
// from a local YAML at FUND_GATEWAY_CONFIG_PATH. The Nacos branch uses the open
// HTTP API only, keeping dependencies minimal; production deployments MAY swap in
// nacos-sdk-go for long-polling once the skeleton is past phase 0.
func Load() (*Config, error) {
	if server := os.Getenv("FUND_GATEWAY_NACOS_SERVER"); server != "" {
		return loadFromNacos(server)
	}
	path := os.Getenv("FUND_GATEWAY_CONFIG_PATH")
	if path == "" {
		path = "configs/fund-provider.dev.yaml"
	}
	return loadFromFile(path)
}

func loadFromFile(path string) (*Config, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read config file %s: %w", path, err)
	}
	cfg := &Config{}
	if err := yaml.Unmarshal(raw, cfg); err != nil {
		return nil, fmt.Errorf("parse yaml %s: %w", path, err)
	}
	cfg.Normalise()
	return cfg, nil
}

func loadFromNacos(server string) (*Config, error) {
	dataID := getenvDefault("FUND_GATEWAY_NACOS_DATAID", "fund-provider.yaml")
	group := getenvDefault("FUND_GATEWAY_NACOS_GROUP", "FUND_PROVIDER_GROUP")
	tenant := os.Getenv("FUND_GATEWAY_NACOS_NAMESPACE")

	endpoint := fmt.Sprintf("%s/nacos/v1/cs/configs", server)
	q := url.Values{}
	q.Set("dataId", dataID)
	q.Set("group", group)
	if tenant != "" {
		q.Set("tenant", tenant)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint+"?"+q.Encode(), nil)
	if err != nil {
		return nil, fmt.Errorf("build nacos request: %w", err)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("call nacos %s: %w", endpoint, err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("nacos returned status %d for dataId=%s group=%s", resp.StatusCode, dataID, group)
	}
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read nacos body: %w", err)
	}
	cfg := &Config{}
	if err := yaml.Unmarshal(body, cfg); err != nil {
		return nil, fmt.Errorf("parse nacos yaml: %w", err)
	}
	cfg.Normalise()
	return cfg, nil
}

func getenvDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
