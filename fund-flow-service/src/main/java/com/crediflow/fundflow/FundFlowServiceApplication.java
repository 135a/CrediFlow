package com.crediflow.fundflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.crediflow")
public class FundFlowServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FundFlowServiceApplication.class, args);
    }
}
