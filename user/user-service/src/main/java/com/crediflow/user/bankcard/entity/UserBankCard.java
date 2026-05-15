package com.crediflow.user.bankcard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crediflow.common.mybatis.SensitiveDataCryptoTypeHandler;
import lombok.Data;

import java.util.Date;

/**
 * 银行卡四要素绑卡事实表；对外仅透出 {@code bindCardId} + 掩码。
 */
@Data
@TableName(value = "cf_user_bank_card", autoResultMap = true)
public class UserBankCard {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private String bindCardId;
    private String bankCode;

    @TableField(value = "card_no", typeHandler = SensitiveDataCryptoTypeHandler.class)
    private String cardNo;
    private String cardNoMask;

    @TableField(value = "reserved_phone", typeHandler = SensitiveDataCryptoTypeHandler.class)
    private String reservedPhone;
    private String reservedPhoneMask;

    private String cardNoFingerprint;

    /** PENDING / VERIFIED / FAILED / UNBOUND */
    private String status;

    private Boolean isPrimary;

    private String providerId;
    private String providerTxnNo;

    private Date verifiedAt;
    private Date unboundAt;

    private Date createdAt;
    private Date updatedAt;
}
