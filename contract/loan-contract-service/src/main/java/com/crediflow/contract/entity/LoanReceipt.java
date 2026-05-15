package com.crediflow.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 贷款收据实体类
 * 用于存储贷款收据相关信息
 */
@Data
@TableName("cf_loan_receipt")
public class LoanReceipt {
    /**
     * 主键ID
     * 使用雪花算法生成分布式ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 收据编号
     * 唯一标识一笔贷款收据
     */
    private String receiptNo;
    /**
     * 关联的贷款申请ID
     * 用于关联到具体的贷款申请记录
     */
    private Long applicationId;
    /**
     * 用户ID
     * 标识该贷款收据所属的用户
     */
    private Long userId;
    /**
     * 本金金额
     * 表示贷款的本金数额
     */
    private BigDecimal principalAmount;
    /**
     * 年利率
     * 表示贷款的年化利率
     */
    private BigDecimal annualInterestRate;
    /**
     * 总期数
     * 表示贷款的总还款期数
     */
    private Integer totalTerms;
    /**
     * 状态
     * 表示贷款收据的当前状态：
     * ACTIVE - 活跃状态（正常还款中）
     * SETTLED - 已结清状态
     * OVERDUE - 逾期状态
     */
    private String status; // ACTIVE, SETTLED, OVERDUE
    /**
     * 创建时间
     * 记录贷款收据的创建时间
     */
    private Date createdAt;
    /**
     * 更新时间
     * 记录贷款收据的最后更新时间
     */
    private Date updatedAt;
}
