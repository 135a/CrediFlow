package com.crediflow.user.bankcard.model;

/**
 * 四要素鉴权命令。
 * <p>{@code realName} 与 {@code idCardNo} 必须由服务端从已 VERIFIED 的 KYC 事实表注入，禁止由前端透传。</p>
 */
public record BankCardVerifyCommand(
        long userId,
        String realName,
        String idCardNo,
        String cardNo,
        String bankCode,
        String reservedPhone) {}
