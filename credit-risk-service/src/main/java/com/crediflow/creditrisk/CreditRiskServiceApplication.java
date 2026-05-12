package com.crediflow.creditrisk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.crediflow")
public class CreditRiskServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CreditRiskServiceApplication.class, args);
    }
}
