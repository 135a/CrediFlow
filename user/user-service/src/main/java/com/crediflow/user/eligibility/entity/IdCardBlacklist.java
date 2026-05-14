package com.crediflow.user.eligibility.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 本地身份证黑名单（仅存指纹，禁止存明文）。
 */
@Data
@TableName("cf_id_card_blacklist")
public class IdCardBlacklist {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String idCardFingerprint;
    private String reasonCode;
    private String reasonDesc;
    private String operator;
    private Date createdAt;
}
