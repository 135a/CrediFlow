package com.crediflow.user.kyc.dto;

import lombok.Data;

@Data
public class KycStep1Request {
    private String realName;
    private String idCardNo;
}
