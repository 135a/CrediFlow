package com.crediflow.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户信用额度实体类
 * 用于存储用户的信用额度相关信息
 */
@Data
@TableName("cf_user_credit_quota")
public class UserCreditQuota {
    /**
     * 主键ID
     * 使用雪花算法生成分布式ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 用户ID
     * 关联用户表的主键
     */
    private Long userId;
    /**
     * 信用总额度
     * 用户可用的总信用额度
     */
    private BigDecimal totalAmount;
    /**
     * 已使用额度
     * 用户已使用的信用额度
     */
    private BigDecimal usedAmount;
    /**
     * 可用额度
     * 用户当前可用的信用额度
     */
    private BigDecimal availableAmount;
    /**
     * 冻结额度
     * 被冻结无法使用的信用额度
     */
    private BigDecimal frozenAmount;
    /**
     * 乐观锁版本号
     * 用于实现乐观锁机制
     */
    @Version
    private Integer version;
    /**
     * 创建时间
     * 记录记录的创建时间
     */
    private Date createdAt;
    /**
     * 更新时间
     * 记录记录的最后更新时间
     */
    private Date updatedAt;
}
