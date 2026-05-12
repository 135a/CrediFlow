package com.crediflow.common.config;

import com.crediflow.common.crypto.SensitiveDataCrypto;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * 生产 profile 下缺少 {@link SensitiveDataCrypto#ENV_KEY} 时拒绝启动。
 */
public class EncryptionEnvironmentValidator implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();
        if (!env.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        String key = env.getProperty("CREDIFLOW_AES256_KEY");
        if (key == null || key.isBlank()) {
            key = System.getenv(SensitiveDataCrypto.ENV_KEY);
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "生产环境必须设置 " + SensitiveDataCrypto.ENV_KEY + "（32 字节 AES 密钥的 Base64）");
        }
    }
}
