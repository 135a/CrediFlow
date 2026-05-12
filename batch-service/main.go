package main

import (
	"crediflow/batch-service/config"
	"crediflow/batch-service/jobs"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/robfig/cron/v3"
)

func main() {
	cfg := config.LoadConfig()
	log.Println("Starting batch-service with config:", cfg)

	c := cron.New(cron.WithSeconds())

	// 10.2 代扣任务
	if cfg.Jobs.DeductEnabled {
		c.AddFunc(cfg.Jobs.DeductCron, func() {
			jobs.RunDeductJob(cfg)
		})
		log.Println("Registered Deduct Job:", cfg.Jobs.DeductCron)
	}

	// 10.3 逾期巡检等其他任务
	if cfg.Jobs.OverdueEnabled {
		c.AddFunc(cfg.Jobs.OverdueCron, func() {
			jobs.RunOverdueJob(cfg)
		})
		log.Println("Registered Overdue Job:", cfg.Jobs.OverdueCron)
	}

	c.Start()
	log.Println("Scheduler started. Waiting for tasks to trigger...")

	// 监听中断信号平滑退出
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down scheduler...")
	c.Stop()
	log.Println("Exited")
}
