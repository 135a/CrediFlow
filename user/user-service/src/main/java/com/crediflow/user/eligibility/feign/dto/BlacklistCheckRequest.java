package com.crediflow.user.eligibility.feign.dto;

import lombok.Data;

@Data
public class BlacklistCheckRequest {
    private String idCardFingerprint;
}
