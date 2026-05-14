package com.crediflow.user.realname.signature;

import com.crediflow.user.realname.config.RealnameProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class RealnameSignatureStrategySelector {

    private final RealnameProperties properties;
    private final Map<String, RealnameSignatureStrategy> strategiesByBeanName;

    public RealnameSignatureStrategySelector(
            RealnameProperties properties,
            Map<String, RealnameSignatureStrategy> strategiesByBeanName) {
        this.properties = properties;
        this.strategiesByBeanName = strategiesByBeanName;
    }

    public RealnameSignatureStrategy resolve() {
        String configured = properties.getProviderType() == null ? "noop" : properties.getProviderType().trim();
        String beanName = toBeanName(configured);
        RealnameSignatureStrategy s = strategiesByBeanName.get(beanName);
        if (s != null) {
            return s;
        }
        s = strategiesByBeanName.get(configured);
        if (s != null) {
            return s;
        }
        throw new IllegalStateException("未找到实名签名策略 Bean: " + configured
                + "；可用: " + strategiesByBeanName.keySet());
    }

    private static String toBeanName(String shortOrBean) {
        String k = shortOrBean.toLowerCase(Locale.ROOT);
        return switch (k) {
            case "noop" -> "noopRealnameSignatureStrategy";
            case "hmacsha256", "hmac_sha256", "hmac" -> "hmacSha256RealnameSignatureStrategy";
            case "groovy", "groovyscript" -> "groovyScriptRealnameSignatureStrategy";
            default -> shortOrBean;
        };
    }
}
