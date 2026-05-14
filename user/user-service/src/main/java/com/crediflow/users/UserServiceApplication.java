package com.crediflow.users;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.crediflow", exclude = { SecurityAutoConfiguration.class })
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.crediflow")
@MapperScan(basePackages = {
        "com.crediflow.user.mapper",
        "com.crediflow.user.kyc.mapper",
        "com.crediflow.user.bankcard.mapper",
        "com.crediflow.user.eligibility.mapper",
        "com.crediflow.user.face.mapper"
})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
