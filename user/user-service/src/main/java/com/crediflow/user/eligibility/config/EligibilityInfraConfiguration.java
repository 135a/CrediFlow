package com.crediflow.user.eligibility.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KycEligibilityProperties.class)
public class EligibilityInfraConfiguration {
}
