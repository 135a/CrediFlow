package com.crediflow.credit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.crediflow")
@EnableDiscoveryClient
@EnableFeignClients
public class CreditRiskApplication {
    public static void main(String[] args) {
        SpringApplication.run(CreditRiskApplication.class, args);
    }
}
