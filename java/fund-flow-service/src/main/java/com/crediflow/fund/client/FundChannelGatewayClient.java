package com.crediflow.fund.client;

import com.crediflow.fund.client.dto.FundChannelDisburseRequest;
import com.crediflow.fund.client.dto.FundChannelDisburseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 调用 Go 资金网关。内网签名由 crediflow-common 全局 {@code InternalAuthRequestInterceptor} 注入；
 * 链路追踪由 {@code FeignTraceInterceptor} 注入 {@code X-Request-Id}。
 */
@FeignClient(name = "fund-channel-gateway", url = "${fund.channel.gateway.url:http://fund-channel-gateway:8090}")
public interface FundChannelGatewayClient {

    @PostMapping(value = "/internal/v1/disburse", consumes = "application/json")
    FundChannelDisburseResponse disburse(@RequestBody FundChannelDisburseRequest body);
}
