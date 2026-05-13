package provider

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"sort"
	"strings"
)

// Signer abstracts request signing and response verification per provider. Real
// algorithms (RSA, MD5+salt, HMAC-SHA256, etc.) plug in here without changing
// HTTPProvider. The phase-0 default implementation is HMAC-SHA256 over a sorted
// concatenation, which is a common starting point but MUST be replaced per
// vendor contract in later batches.
type Signer interface {
	Sign(payload map[string]any) (map[string]any, error)
	Verify(payload map[string]any) (map[string]any, error)
}

// Cipher abstracts the encryption of sensitive fields. Vendors typically demand
// AES-CBC, AES-GCM, or RSA-OAEP for fields like cardNo / idNo. Phase-0 supplies
// a passthrough implementation so the seam is exercised end-to-end; production
// batches will subclass per algorithm.
type Cipher interface {
	EncryptFields(payload map[string]any) (map[string]any, error)
	DecryptFields(payload map[string]any) (map[string]any, error)
}

type hmacSigner struct {
	algorithm string
	secret    []byte
}

func NewSigner(algorithm, secret string) Signer {
	alg := strings.ToUpper(strings.TrimSpace(algorithm))
	if alg == "" {
		alg = "HMAC_SHA256"
	}
	return &hmacSigner{algorithm: alg, secret: []byte(secret)}
}

func (h *hmacSigner) Sign(payload map[string]any) (map[string]any, error) {
	if h.algorithm != "HMAC_SHA256" {
		// Real implementations land here for RSA / MD5+salt etc.
		return nil, fmt.Errorf("signer: algorithm %q not implemented in phase-0 stub", h.algorithm)
	}
	if len(h.secret) == 0 {
		return nil, errors.New("signer: empty secret")
	}
	out := make(map[string]any, len(payload)+2)
	for k, v := range payload {
		out[k] = v
	}
	canonical := canonicalString(payload)
	mac := hmac.New(sha256.New, h.secret)
	_, _ = mac.Write([]byte(canonical))
	out["signAlg"] = h.algorithm
	out["sign"] = hex.EncodeToString(mac.Sum(nil))
	return out, nil
}

func (h *hmacSigner) Verify(payload map[string]any) (map[string]any, error) {
	// Phase-0 stub: provider responses are not yet specified, so we accept any
	// payload missing a "sign" field (mock provider). When a sign field is
	// present we verify with the same algorithm. Replace per vendor contract.
	sig, ok := payload["sign"].(string)
	if !ok || sig == "" {
		return payload, nil
	}
	clone := make(map[string]any, len(payload))
	for k, v := range payload {
		if k == "sign" || k == "signAlg" {
			continue
		}
		clone[k] = v
	}
	canonical := canonicalString(clone)
	mac := hmac.New(sha256.New, h.secret)
	_, _ = mac.Write([]byte(canonical))
	expected := hex.EncodeToString(mac.Sum(nil))
	if !hmac.Equal([]byte(expected), []byte(sig)) {
		return nil, errors.New("response signature mismatch")
	}
	return payload, nil
}

func canonicalString(payload map[string]any) string {
	keys := make([]string, 0, len(payload))
	for k := range payload {
		if k == "sign" || k == "signAlg" {
			continue
		}
		keys = append(keys, k)
	}
	sort.Strings(keys)
	var b strings.Builder
	for _, k := range keys {
		b.WriteString(k)
		b.WriteByte('=')
		b.WriteString(fmt.Sprintf("%v", payload[k]))
		b.WriteByte('&')
	}
	return strings.TrimRight(b.String(), "&")
}

type passthroughCipher struct {
	fields []string
}

// NewCipher creates the per-provider Cipher. Phase-0 returns a passthrough
// implementation that leaves payloads as-is — the encryptFields list is still
// recorded so the seam is testable; real AES/RSA implementations replace this.
func NewCipher(aesKey, rsaPub, rsaPriv string, fields []string) Cipher {
	_ = aesKey
	_ = rsaPub
	_ = rsaPriv
	return &passthroughCipher{fields: fields}
}

func (p *passthroughCipher) EncryptFields(payload map[string]any) (map[string]any, error) {
	return payload, nil
}

func (p *passthroughCipher) DecryptFields(payload map[string]any) (map[string]any, error) {
	return payload, nil
}
