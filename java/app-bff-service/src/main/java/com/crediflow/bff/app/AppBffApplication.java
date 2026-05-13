package com.crediflow.bff.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.crediflow")
public class AppBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(AppBffApplication.class, args);
    }
}
