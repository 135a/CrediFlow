package com.crediflow.repayment.client;

import com.crediflow.repayment.client.dto.FundChannelRepayRequest;
import com.crediflow.repayment.client.dto.FundChannelRepayResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 调用 Go {@code fund-channel-gateway} 的主动还款 / 代扣受理接口。
 *
 * <p>内网签名由 {@code InternalAuthRequestInterceptor} 全局注入；
 * {@code X-Request-Id} 由 {@code FeignTraceInterceptor} 注入。</p>
 *
 * <p>使用 {@code contextId} 与 fund-flow-service 的同名 {@code @FeignClient} 区分，
 * 避免 Spring 注册同名 bean 冲突。</p>
 */
@FeignClient(name = "fund-channel-gateway", contextId = "repaymentFundGateway",
        url = "${fund.channel.gateway.url:http://fund-channel-gateway:8090}")
public interface FundChannelGatewayClient {

    @PostMapping(value = "/internal/v1/repay", consumes = "application/json")
    FundChannelRepayResponse repay(@RequestBody FundChannelRepayRequest body);

    @PostMapping(value = "/internal/v1/withhold", consumes = "application/json")
    FundChannelRepayResponse withhold(@RequestBody FundChannelRepayRequest body);
}
