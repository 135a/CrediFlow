package main

import (
	"fmt"
	"log"
	"net/http"
	"time"

	"github.com/robfig/cron/v3"
)

func main() {
	log.Println("Starting Scheduler-Go service...")
	
	c := cron.New(cron.WithSeconds())
	
	// 6.2 每天凌晨准点触发 Java 端逾期计算接口
	c.AddFunc("0 1 0 * * *", func() {
		log.Println("Cron [0 1 0 * * *] triggered: Running overdue inspection...")
		resp, err := http.Post("http://post-loan-service:8080/api/internal/post-loan/trigger-overdue-inspection", "application/json", nil)
		if err != nil {
			log.Printf("Failed to trigger overdue inspection: %v", err)
			return
		}
		defer resp.Body.Close()
		log.Printf("Overdue inspection triggered successfully, status: %d", resp.StatusCode)
	})

	// 6.3 每天早上8点触发自动代扣还款
	c.AddFunc("0 0 8 * * *", func() {
		log.Println("Cron [0 0 8 * * *] triggered: Running auto-repayment deduction...")
		// Assuming we have an endpoint in repayment-service for this
		resp, err := http.Post("http://repayment-service:8080/api/internal/repayment/auto-deduct", "application/json", nil)
		if err != nil {
			log.Printf("Failed to trigger auto-deduct: %v", err)
			return
		}
		defer resp.Body.Close()
		log.Printf("Auto-deduct triggered successfully, status: %d", resp.StatusCode)
	})

	c.Start()
	
	// mock health check
	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, "OK")
	})
	
	log.Fatal(http.ListenAndServe(":8081", nil))
}
