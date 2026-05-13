package api

import (
	"net/http"

	"crediflow/fund-channel-gateway/audit"
	"crediflow/fund-channel-gateway/config"
	"crediflow/fund-channel-gateway/idempotency"
	"crediflow/fund-channel-gateway/mq"
	"crediflow/fund-channel-gateway/provider"

	"github.com/gin-gonic/gin"
)

// Deps wires all collaborators into the HTTP layer. Constructed once in main()
// and passed by pointer to keep handlers stateless and unit-testable.
type Deps struct {
	Config        *config.Config
	Idempotency   idempotency.Store
	Publisher     mq.Publisher
	ProviderReg   *provider.Registry
	AuditRecorder audit.Recorder
}

type Server struct {
	engine *gin.Engine
	deps   *Deps
}

func NewServer(deps *Deps) *Server {
	if deps.Config.IsProduction() {
		gin.SetMode(gin.ReleaseMode)
	}
	r := gin.New()
	// Order matters: trace must be installed before recovery so panic logs carry the trace id.
	r.Use(TraceMiddleware(), RecoveryMiddleware())

	s := &Server{engine: r, deps: deps}

	// Public probes — no internal-sign required.
	r.GET("/health", s.handleHealth)
	r.GET("/ready", s.handleReady)

	// Provider async callbacks (one URL per providerId). Authenticated by the
	// provider's own request signature; NOT by X-Internal-Sign.
	r.POST("/fund/callback/:providerId", s.handleCallback)

	// Internal Java-only API — all protected by X-Internal-Sign.
	internal := r.Group("/internal/v1")
	internal.Use(InternalSignMiddleware(deps.Config.InternalSign))
	{
		internal.POST("/disburse", s.handleDisburse)
		internal.POST("/repay", s.handleRepay)
		internal.POST("/withhold", s.handleWithhold)
	}

	return s
}

// ServeHTTP makes Server satisfy http.Handler so main can pass it straight to
// http.Server without going through Gin's Run helper.
func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	s.engine.ServeHTTP(w, r)
}

func (s *Server) Engine() *gin.Engine { return s.engine }
