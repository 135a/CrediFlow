package com.crediflow.user.eligibility.feign.dto;

import lombok.Data;

@Data
public class BlacklistCheckResponse {
    private boolean hit;
    private String reasonCode;
}
