package com.crediflow.repayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.crediflow")
@EnableDiscoveryClient
@EnableFeignClients
public class RepaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RepaymentServiceApplication.class, args);
    }
}
