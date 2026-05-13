package com.crediflow.user.realname.signature;

import com.crediflow.user.realname.config.RealnameProperties;
import com.crediflow.user.realname.model.RealnameVerifyCommand;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Groovy 脚本占位：绑定 body、appSecret、appKey、timestamp、realName、idCardNo，脚本应返回 String。
 */
@Component("groovyScriptRealnameSignatureStrategy")
public class GroovyScriptRealnameSignatureStrategy implements RealnameSignatureStrategy {

    private final RealnameProperties properties;

    public GroovyScriptRealnameSignatureStrategy(RealnameProperties properties) {
        this.properties = properties;
    }

    @Override
    public String sign(String bodyWithPlaceholdersResolvedExceptSignature, RealnameVerifyCommand command, long timestampMillis) {
        if (!StringUtils.hasText(properties.getSignatureScript())) {
            return "";
        }
        Binding binding = new Binding();
        binding.setVariable("body", bodyWithPlaceholdersResolvedExceptSignature);
        binding.setVariable("appSecret", properties.getAppSecret());
        binding.setVariable("appKey", properties.getAppKey());
        binding.setVariable("timestamp", timestampMillis);
        binding.setVariable("realName", command.realName());
        binding.setVariable("idCardNo", command.idCardNo());
        GroovyShell shell = new GroovyShell(binding);
        Object v = shell.evaluate(properties.getSignatureScript());
        return v == null ? "" : v.toString();
    }
}
