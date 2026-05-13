package com.crediflow.user.bankcard.model;

/**
 * 四要素鉴权结果。
 */
public record BankCardVerifyResult(boolean success, String providerTxnNo, String internalFailureCode) {}
