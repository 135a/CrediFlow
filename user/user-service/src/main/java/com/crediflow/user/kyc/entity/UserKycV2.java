package com.crediflow.user.kyc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crediflow.common.mybatis.SensitiveDataCryptoTypeHandler;
import lombok.Data;

import java.util.Date;

/**
 * KYC v2 事实表。
 * <p>每个用户唯一一行；{@code idCardFingerprint} 全局唯一保证「一人一证一账号」。</p>
 */
@Data
@TableName(value = "cf_user_kyc_v2", autoResultMap = true)
public class UserKycV2 {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String realName;

    /** AES 密文，通过 {@link SensitiveDataCryptoTypeHandler} 透明加解密。 */
    @TableField(value = "id_card_no", typeHandler = SensitiveDataCryptoTypeHandler.class)
    private String idCardNo;

    private String idCardMask;
    private String idCardFingerprint;
    private Integer ageAtSubmit;

    /** NOT_SUBMITTED / PASS / REJECTED_AGE / REJECTED_DUP / REJECTED_BLACKLIST / REJECTED_RATE_LIMIT */
    private String eligibilityStatus;
    private Date eligibilityDecidedAt;

    /** NOT_SUBMITTED / PROCESSING / VERIFIED / FAILED */
    private String realnameStatus;
    private String realnameProviderTxnNo;
    private Date realnameVerifiedAt;

    /** NOT_SUBMITTED / PROCESSING / VERIFIED / FAILED */
    private String faceStatus;
    private String faceProviderId;
    private String faceProviderBizNo;
    private String faceProviderTxnNo;
    private Date faceVerifiedAt;
    /** 内部原因码，禁止对外。 */
    private String faceFailureCode;

    private Boolean kycPassed;
    private Date kycPassedAt;

    private Date createdAt;
    private Date updatedAt;
}
