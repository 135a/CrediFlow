package com.crediflow.user.eligibility.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KYC 准入闸门配置（Nacos / application.yml）。
 *
 * <pre>
 * crediflow.kyc.eligibility.age.min=18
 * crediflow.kyc.eligibility.age.max=55
 * crediflow.kyc.eligibility.risk-service-enabled=true
 * </pre>
 */
@ConfigurationProperties(prefix = "crediflow.kyc.eligibility")
public class KycEligibilityProperties {

    private Age age = new Age();
    /** 关闭后 BlacklistPolicy 仅做本地黑名单校验（用于灰度 / 风控接口未就绪）。 */
    private boolean riskServiceEnabled = true;

    public Age getAge() {
        return age;
    }

    public void setAge(Age age) {
        this.age = age == null ? new Age() : age;
    }

    public boolean isRiskServiceEnabled() {
        return riskServiceEnabled;
    }

    public void setRiskServiceEnabled(boolean riskServiceEnabled) {
        this.riskServiceEnabled = riskServiceEnabled;
    }

    public static class Age {
        private int min = 18;
        private int max = 55;

        public int getMin() {
            return min;
        }

        public void setMin(int min) {
            this.min = min;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }
    }
}
