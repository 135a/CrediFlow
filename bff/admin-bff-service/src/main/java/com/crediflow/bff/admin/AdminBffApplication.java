package com.crediflow.bff.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 管理业务bff(Backend for Frontend)应用程序的主入口类
 * 使用@SpringBootApplication注解标记这是一个Spring Boot应用程序
 * scanBasePackages属性指定了组件扫描的基础包为"com.crediflow"
 */
@SpringBootApplication(scanBasePackages = "com.crediflow")
@EnableFeignClients(basePackages = "com.crediflow.bff.admin.feign")
public class AdminBffApplication {
    /**
     * 应用程序的主方法，是Spring Boot应用程序的入口点
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 使用SpringApplication.run方法启动Spring Boot应用程序
        SpringApplication.run(AdminBffApplication.class, args);
    }
}
