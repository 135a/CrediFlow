package com.crediflow.common.config;

import com.crediflow.common.crypto.SensitiveDataCrypto;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * 生产 profile 下缺少 {@link SensitiveDataCrypto#ENV_KEY} 时拒绝启动。
 * 该类实现 ApplicationContextInitializer 接口，用于在 Spring 应用上下文初始化时进行环境验证。
 */
public class EncryptionEnvironmentValidator implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        // 获取应用的环境配置
        ConfigurableEnvironment env = context.getEnvironment();
        // 检查当前激活的 profile 是否包含 "prod"，如果不是则直接返回
        if (!env.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        // 尝试从环境变量中获取 AES 密钥，首先从配置属性中获取
        String key = env.getProperty("CREDIFLOW_AES256_KEY");
        // 如果配置属性中没有获取到密钥，则尝试从系统环境变量中获取
        if (key == null || key.isBlank()) {
            key = System.getenv(SensitiveDataCrypto.ENV_KEY);
        }
        // 如果最终仍未获取到有效的密钥，则抛出 IllegalStateException 异常
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "生产环境必须设置 " + SensitiveDataCrypto.ENV_KEY + "（32 字节 AES 密钥的 Base64）");
        }
    }
}
