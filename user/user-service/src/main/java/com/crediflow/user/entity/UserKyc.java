package com.crediflow.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crediflow.common.mybatis.SensitiveDataCryptoTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName(value = "cf_user_kyc", autoResultMap = true)
public class UserKyc {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private BigDecimal monthlyIncome;
    private Date birthDate;
    private String residence;
    private String occupation;
    private String realName;
    @TableField(value = "id_card_no", typeHandler = SensitiveDataCryptoTypeHandler.class)
    private String idCardNo;
    private Integer age;
    private Boolean faceVerified;
    private String paymentMethod;
    private String paymentAccount;
    private Integer stepStatus;
    /** NOT_SUBMITTED / PROCESSING / VERIFIED / FAILED */
    private String realnameStatus;
    private Date realnameVerifiedAt;
    private String realnameProviderTxnNo;
    private String idCardMask;
    private String idCardFingerprint;
    /** 内部原因码，禁止对外返回 */
    private String realnameInternalReason;
    private Date createdAt;
    private Date updatedAt;
}
