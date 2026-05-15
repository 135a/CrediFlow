package com.crediflow.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 信贷结果实体类
 * 用于存储用户的信贷额度信息
 */
@Data
@TableName("cf_credit_result")
public class CreditResult {
    /**
     * 主键ID
     * 使用ASSIGN_ID策略自动生成
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 用户ID
     * 关联到用户表的主键
     */
    private Long userId;
    /**
     * 信贷总额度
     * 用户可用的最大信贷金额
     */
    private BigDecimal creditAmount;
    /**
     * 已使用额度
     * 用户已经使用的信贷金额
     */
    private BigDecimal usedAmount;
    /**
     * 信贷状态
     * 枚举值：ACTIVE(激活), FROZEN(冻结), EXPIRED(过期)
     */
    private String status; // ACTIVE, FROZEN, EXPIRED
    /**
     * 过期时间
     * 信贷额度的失效时间
     */
    private Date expireTime;
    /**
     * 创建时间
     * 记录信贷额度的创建时间
     */
    private Date createdAt;
    /**
     * 更新时间
     * 记录信贷额度的最后修改时间
     */
    private Date updatedAt;
}
