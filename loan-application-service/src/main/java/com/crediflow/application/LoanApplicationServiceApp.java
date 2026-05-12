package com.crediflow.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.crediflow")
@EnableDiscoveryClient
@EnableFeignClients
@org.springframework.scheduling.annotation.EnableScheduling
public class LoanApplicationServiceApp {
    public static void main(String[] args) {
        SpringApplication.run(LoanApplicationServiceApp.class, args);
    }
}
