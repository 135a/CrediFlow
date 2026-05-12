package main

import (
	"crediflow/batch-service/config"
	"crediflow/batch-service/jobs"
	"crediflow/batch-service/lock"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"github.com/robfig/cron/v3"
)

// jobRegistry 全局任务注册表，供管理接口查询和手动触发
type jobEntry struct {
	Name    string `json:"name"`
	Cron    string `json:"cron"`
	Enabled bool   `json:"enabled"`
	RunFunc func()
}

var registry []jobEntry

func main() {
	cfg := config.LoadConfig()
	log.Println("========================================")
	log.Println("  CrediFlow Batch Scheduler Starting...")
	log.Println("========================================")

	// 初始化 Redis 分布式锁
	lock.Init(cfg.RedisAddr, cfg.RedisPassword, cfg.RedisDB)

	c := cron.New(cron.WithSeconds())

	// 注册全部 6 个 Job
	registry = []jobEntry{
		{Name: "DeductJob", Cron: cfg.Jobs.DeductCron, Enabled: cfg.Jobs.DeductEnabled,
			RunFunc: func() { jobs.RunDeductJob(cfg) }},
		{Name: "OverdueJob", Cron: cfg.Jobs.OverdueCron, Enabled: cfg.Jobs.OverdueEnabled,
			RunFunc: func() { jobs.RunOverdueJob(cfg) }},
		{Name: "PenaltyJob", Cron: cfg.Jobs.PenaltyCron, Enabled: cfg.Jobs.PenaltyEnabled,
			RunFunc: func() { jobs.RunPenaltyJob(cfg) }},
		{Name: "ReminderJob", Cron: cfg.Jobs.ReminderCron, Enabled: cfg.Jobs.ReminderEnabled,
			RunFunc: func() { jobs.RunReminderJob(cfg) }},
		{Name: "RiskDispatchJob", Cron: cfg.Jobs.RiskDispatchCron, Enabled: cfg.Jobs.RiskDispatchEnabled,
			RunFunc: func() { jobs.RunRiskDispatchJob(cfg) }},
		{Name: "NotificationJob", Cron: cfg.Jobs.NotificationCron, Enabled: cfg.Jobs.NotificationEnabled,
			RunFunc: func() { jobs.RunNotificationJob(cfg) }},
	}

	for _, entry := range registry {
		if entry.Enabled {
			e := entry // capture loop variable
			c.AddFunc(e.Cron, e.RunFunc)
			log.Printf("[Scheduler] Registered %-20s cron=%s\n", e.Name, e.Cron)
		} else {
			log.Printf("[Scheduler] %-20s DISABLED\n", entry.Name)
		}
	}

	c.Start()
	log.Println("[Scheduler] All jobs registered. Cron scheduler running.")

	// 启动 HTTP 管理接口（非阻塞）
	go startAdminServer(cfg.AdminPort)

	// 监听中断信号平滑退出
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("[Scheduler] Shutting down...")
	c.Stop()
	log.Println("[Scheduler] Exited gracefully.")
}

// ─── HTTP 管理接口 ──────────────────────────────────────────

func startAdminServer(port string) {
	mux := http.NewServeMux()

	// GET /health - 健康检查
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{
			"status":  "UP",
			"service": "batch-service",
		})
	})

	// GET /jobs - 列出所有注册的任务及状态
	mux.HandleFunc("/jobs", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		type jobInfo struct {
			Name    string `json:"name"`
			Cron    string `json:"cron"`
			Enabled bool   `json:"enabled"`
		}
		var list []jobInfo
		for _, e := range registry {
			list = append(list, jobInfo{Name: e.Name, Cron: e.Cron, Enabled: e.Enabled})
		}
		json.NewEncoder(w).Encode(list)
	})

	// POST /trigger?job=DeductJob - 手动触发指定任务
	mux.HandleFunc("/trigger", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		jobName := r.URL.Query().Get("job")
		if jobName == "" {
			http.Error(w, `{"error":"missing ?job= parameter"}`, http.StatusBadRequest)
			return
		}

		for _, e := range registry {
			if e.Name == jobName {
				log.Printf("[Admin] Manual trigger: %s\n", jobName)
				go e.RunFunc() // 异步执行，不阻塞 HTTP 响应
				w.Header().Set("Content-Type", "application/json")
				json.NewEncoder(w).Encode(map[string]string{
					"status":  "TRIGGERED",
					"job":     jobName,
					"message": "Job triggered asynchronously",
				})
				return
			}
		}

		http.Error(w, fmt.Sprintf(`{"error":"job '%s' not found"}`, jobName), http.StatusNotFound)
	})

	addr := fmt.Sprintf(":%s", port)
	log.Printf("[Admin] HTTP management server listening on %s\n", addr)
	log.Printf("[Admin] Endpoints: GET /health, GET /jobs, POST /trigger?job=<name>\n")

	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Printf("[Admin] Server error: %v\n", err)
	}
}
