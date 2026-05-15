package com.crediflow.contract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Spring Boot应用程序的主启动类
 * 使用@SpringBootApplication注解标记这是一个Spring Boot应用程序
 * scanBasePackages属性指定了组件扫描的基础包路径为"com.crediflow"
 */
@SpringBootApplication(scanBasePackages = "com.crediflow")
/**
 * 启用服务发现客户端功能，允许该服务注册到服务注册中心
 */
@EnableDiscoveryClient
/**
 * 启用Feign客户端功能，支持声明式服务调用
 */
@EnableFeignClients
public class LoanContractApplication {
    /**
     * 程序入口方法
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 使用SpringApplication.run方法启动Spring Boot应用程序
        SpringApplication.run(LoanContractApplication.class, args);
    }
}
