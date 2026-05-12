package com.crediflow.fund.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.fund.entity.FundFlow;
import com.crediflow.fund.mapper.FundFlowMapper;
import com.crediflow.fund.service.FundFlowService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Service
public class FundFlowServiceImpl extends ServiceImpl<FundFlowMapper, FundFlow> implements FundFlowService {

    @Override
    public void recordFlow(Long userId, String type, BigDecimal amount, String tradeNo, String status) {
        FundFlow flow = new FundFlow();
        flow.setUserId(userId);
        flow.setType(type);
        flow.setAmount(amount);
        flow.setTradeNo(tradeNo);
        flow.setStatus(status);
        flow.setTradeTime(new Date());
        flow.setCreatedAt(new Date());
        flow.setUpdatedAt(new Date());
        this.save(flow);
        
        System.out.println("FUND_FLOW: Recorded " + type + " of amount " + amount + " for user " + userId);
    }

    /**
     * TODO: 接入真实支付宝转账接口
     * 此处应调用 AlipayFundTransUniTransferClient 等官方 SDK，
     * 将资金从对公账户实时打入用户绑定的支付宝账号或银行卡中。
     * 参数应包含：收款方账号 (payee_info), 转账金额 (trans_amount), 业务备注等。
     * 成功后获取真实的 tradeNo 再落库。
     */
    public void executeAlipayTransfer(Long userId, BigDecimal amount) {
        // 伪代码:
        // AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do", APP_ID, APP_PRIVATE_KEY, "json", "UTF-8", ALIPAY_PUBLIC_KEY, "RSA2");
        // AlipayFundTransUniTransferRequest request = new AlipayFundTransUniTransferRequest();
        // request.setBizContent("{" +
        //         "\"out_biz_no\":\"" + UUID.randomUUID().toString() + "\"," +
        //         "\"trans_amount\":\"" + amount + "\"," +
        //         "\"product_code\":\"TRANS_ACCOUNT_NO_PWD\"," +
        //         "\"biz_scene\":\"DIRECT_TRANSFER\"," +
        //         "\"payee_info\":{" +
        //         "\"identity\":\"用户支付宝账号/卡号\"," +
        //         "\"identity_type\":\"ALIPAY_LOGON_ID\"," +
        //         "\"name\":\"用户真实姓名\"" +
        //         "}," +
        //         "\"remark\":\"CrediFlow 信贷放款\"" +
        //         "}");
        // AlipayFundTransUniTransferResponse response = alipayClient.execute(request);
        // if(response.isSuccess()){
        //     recordFlow(userId, "DISBURSE", amount, response.getOrderId(), "SUCCESS");
        // } else {
        //     // 处理失败
        // }
    }

    @Override
    public boolean verifyThirdPartyCallback(Map<String, String> params) {
        // 第三方支付回调验签占位
        // 通常需要使用公钥验证 params 中的 sign 字段
        String sign = params.get("sign");
        if (sign == null || sign.isEmpty()) {
            return false;
        }
        System.out.println("VERIFY_SIGN: Verifying signature: " + sign);
        return true;
    }
}
