package com.crediflow.user.kyc.dto;

import lombok.Data;

@Data
public class KycStep2Request {
    /** 前端 SDK 完成活体后从厂商拿到的业务 token。 */
    private String activeFaceToken;
}
