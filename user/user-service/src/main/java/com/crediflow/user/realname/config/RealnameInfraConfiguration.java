package com.crediflow.user.realname.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RealnameProperties.class)
public class RealnameInfraConfiguration {
}
