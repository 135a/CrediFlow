package com.crediflow.loancontract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.crediflow")
public class LoanContractServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoanContractServiceApplication.class, args);
    }
}
