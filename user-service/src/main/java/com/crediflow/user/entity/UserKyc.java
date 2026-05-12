package com.crediflow.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("cf_user_kyc")
public class UserKyc {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private BigDecimal monthlyIncome;
    private Date birthDate;
    private String residence;
    private String occupation;
    private String realName;
    private String idCardNo;
    private Integer age;
    private Boolean faceVerified;
    private String paymentMethod;
    private String paymentAccount;
    private Integer stepStatus;
    private Date createdAt;
    private Date updatedAt;
}
