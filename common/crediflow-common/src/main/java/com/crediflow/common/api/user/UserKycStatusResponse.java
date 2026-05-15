package com.crediflow.common.api.user;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/** 对外/内部 KYC 状态快照：不含明文证件号与内部原因码。 */
@Data
public class UserKycStatusResponse {
    private Long userId;
    private Integer stepStatus;
    private String realnameStatus;
    private String idCardMask;
    private BigDecimal monthlyIncome;
    private Date birthDate;
    private String residence;
    private String occupation;
    private String realName;
    private Integer age;
    private String paymentMethod;
    private String paymentAccount;
}
