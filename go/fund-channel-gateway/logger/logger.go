package logger

import (
	"fmt"
	"log"
	"os"
	"regexp"
	"strings"
)

var (
	// Sensitive log patterns we proactively redact even if upstream forgot to mask.
	// These are last-line defenses; primary protection is to never pass plaintext at all.
	cardNoPattern   = regexp.MustCompile(`\b\d{13,19}\b`)
	idCardPattern   = regexp.MustCompile(`\b\d{17}[\dXx]\b`)
	secretPattern   = regexp.MustCompile(`(?i)(app[_-]?secret|secret|password|aes[_-]?key|rsa[_-]?private[_-]?key)\s*[=:]\s*\S+`)
	cardJsonPattern = regexp.MustCompile(`("(?:bank_?card_?no|cardNo|cardNumber|idCardNo|id_card_no)"\s*:\s*")[^"]+(")`)
)

func Init() {
	log.SetFlags(log.LstdFlags | log.Lmicroseconds)
	log.SetOutput(os.Stdout)
}

func Info(tag, format string, args ...any) {
	log.Printf("[%s] "+sanitize(format), prependArgs(tag, args)...)
}

func Warn(tag, format string, args ...any) {
	log.Printf("[%s] WARN "+sanitize(format), prependArgs(tag, args)...)
}

func Error(tag, format string, args ...any) {
	log.Printf("[%s] ERROR "+sanitize(format), prependArgs(tag, args)...)
}

func Fatal(tag, format string, args ...any) {
	log.Fatalf("[%s] FATAL "+sanitize(format), prependArgs(tag, args)...)
}

func prependArgs(tag string, args []any) []any {
	out := make([]any, 0, len(args)+1)
	out = append(out, tag)
	for _, a := range args {
		switch v := a.(type) {
		case string:
			out = append(out, sanitize(v))
		default:
			out = append(out, sanitize(fmt.Sprintf("%v", v)))
		}
	}
	return out
}

// sanitize masks obvious sensitive substrings before they reach stdout.
// Production deployments should also pipe stdout through a secret-scrubber sidecar.
func sanitize(s string) string {
	if s == "" {
		return s
	}
	s = cardJsonPattern.ReplaceAllString(s, `$1***MASKED***$2`)
	s = secretPattern.ReplaceAllStringFunc(s, func(m string) string {
		idx := strings.IndexAny(m, "=:")
		if idx < 0 {
			return "***MASKED***"
		}
		return m[:idx+1] + " ***MASKED***"
	})
	s = idCardPattern.ReplaceAllStringFunc(s, mask)
	s = cardNoPattern.ReplaceAllStringFunc(s, mask)
	return s
}

func mask(in string) string {
	if len(in) <= 6 {
		return strings.Repeat("*", len(in))
	}
	return in[:3] + strings.Repeat("*", len(in)-6) + in[len(in)-3:]
}
