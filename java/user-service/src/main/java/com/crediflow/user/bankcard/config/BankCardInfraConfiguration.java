package com.crediflow.user.bankcard.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BankCardProperties.class)
public class BankCardInfraConfiguration {
}
