package com.crediflow.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.crediflow.common.mybatis.SensitiveDataCryptoTypeHandler;
import lombok.Data;

import java.util.Date;

@Data
@TableName(value = "cf_user", autoResultMap = true)
public class User {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(typeHandler = SensitiveDataCryptoTypeHandler.class)
    private String phone;

    private String password;

    @TableField(typeHandler = SensitiveDataCryptoTypeHandler.class)
    private String idCard;

    private String realName;

    private Integer status;

    private Date createdAt;

    private Date updatedAt;
}
