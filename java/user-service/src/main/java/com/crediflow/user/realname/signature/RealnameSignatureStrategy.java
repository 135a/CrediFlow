package com.crediflow.user.realname.signature;

import com.crediflow.user.realname.model.RealnameVerifyCommand;

/**
 * 根据请求体与密钥上下文生成签名字段（写入模板 {{signature}}）。
 */
public interface RealnameSignatureStrategy {

    String sign(String bodyWithPlaceholdersResolvedExceptSignature, RealnameVerifyCommand command, long timestampMillis);
}
