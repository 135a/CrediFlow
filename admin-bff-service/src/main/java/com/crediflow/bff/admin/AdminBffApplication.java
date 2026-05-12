package com.crediflow.bff.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.crediflow")
public class AdminBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminBffApplication.class, args);
    }
}
