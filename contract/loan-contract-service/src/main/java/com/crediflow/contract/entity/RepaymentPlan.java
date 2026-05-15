package com.crediflow.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 还款计划实体类
 * 对应数据库表 cf_repayment_plan
 */
@Data
@TableName("cf_repayment_plan")
public class RepaymentPlan {
    /**
     * 主键ID，使用ASSIGN_ID策略生成
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 收据ID，关联到具体的收据记录
     */
    private Long receiptId;
    /**
     * 用户ID，标识该还款计划所属的用户
     */
    private Long userId;
    /**
     * 期号，表示第几期还款
     */
    private Integer termNo;
    /**
     * 本金金额，使用BigDecimal确保精度
     */
    private BigDecimal principalAmount;
    /**
     * 利息金额，使用BigDecimal确保精度
     */
    private BigDecimal interestAmount;
    /**
     * 应还日期，表示该期还款的到期日期
     */
    private Date dueDate;
    private String status; // PENDING, PAID, OVERDUE
    private Date createdAt;
    private Date updatedAt;
}
