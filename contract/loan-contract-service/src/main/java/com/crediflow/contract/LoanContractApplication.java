package com.crediflow.contract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.crediflow")
@EnableDiscoveryClient
@EnableFeignClients
public class LoanContractApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoanContractApplication.class, args);
    }
}
