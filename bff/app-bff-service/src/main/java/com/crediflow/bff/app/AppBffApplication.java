package com.crediflow.bff.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.crediflow")
@EnableFeignClients
public class AppBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(AppBffApplication.class, args);
    }
}
