package com.crediflow.user.realname.env;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 在 Spring 上下文刷新前执行：生产 profile 下禁止 {@code crediflow.realname.mock-success=true}。
 */
public class RealnameMockSafetyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String PROP_MOCK = "crediflow.realname.mock-success";
    static final String PROP_PROD_PROFILES = "crediflow.realname.prod-profiles";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment env = context.getEnvironment();
        if (!isProduction(env)) {
            return;
        }
        boolean mock = env.getProperty(PROP_MOCK, Boolean.class, false);
        if (mock) {
            throw new IllegalStateException(
                    "生产环境禁止 crediflow.realname.mock-success=true。请关闭 Mock 后重启。");
        }
    }

    /** 供测试与运维文档对齐：与 {@link com.crediflow.user.realname.config.RealnameProperties#getProdProfiles()} 语义一致（此处仅读逗号分隔字符串）。 */
    public static boolean isProduction(Environment env) {
        Set<String> prodNames = new HashSet<>(List.of("prod", "production"));
        String extra = env.getProperty(PROP_PROD_PROFILES);
        if (StringUtils.hasText(extra)) {
            Arrays.stream(extra.split(","))
                    .map(s -> s.trim().toLowerCase(Locale.ROOT))
                    .filter(StringUtils::hasText)
                    .forEach(prodNames::add);
        }
        String[] active = env.getActiveProfiles();
        if (active.length == 0) {
            return false;
        }
        for (String p : active) {
            if (prodNames.contains(p.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
