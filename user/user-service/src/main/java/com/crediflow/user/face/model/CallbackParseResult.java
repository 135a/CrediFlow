package com.crediflow.user.face.model;

import java.util.Map;

/**
 * 厂商异步回调解析结果。
 * <p>terminal 取值固定为 {@code SUCCESS} / {@code FAILED}。</p>
 */
public record CallbackParseResult(
        String providerBizNo,
        String providerTxnNo,
        String terminal,
        String failureCode,
        Map<String, String> normalizedFields) {}
