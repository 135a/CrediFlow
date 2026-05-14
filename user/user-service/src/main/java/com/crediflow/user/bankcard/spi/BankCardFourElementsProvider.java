package com.crediflow.user.bankcard.spi;

import com.crediflow.user.bankcard.model.BankCardVerifyCommand;
import com.crediflow.user.bankcard.model.BankCardVerifyResult;

/**
 * 银行卡四要素鉴权第三方抽象（与资金网关隔离）。
 */
public interface BankCardFourElementsProvider {

    BankCardVerifyResult verify(BankCardVerifyCommand command);

    String providerId();
}
