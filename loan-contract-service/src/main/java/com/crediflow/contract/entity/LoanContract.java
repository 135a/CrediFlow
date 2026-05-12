package com.crediflow.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("cf_loan_contract")
public class LoanContract {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String contractNo;
    private Long applicationId;
    private Long userId;
    private BigDecimal loanAmount;
    private BigDecimal interestRate;
    private Integer term;
    private String status; // EFFECTIVE, CLEARED, OVERDUE
    private Date signTime;
    
    // 我们在此新增两个字段模拟 PDF 链接与日志留存，虽然表可能没创建这两列，但可以用 JSON 或其他方式，
    // 这里为了对应最简合规，假设我们有这列（或我们可以先打印日志代替入库）
    // 为了不破坏原有 flyway 结构，我们通过业务逻辑日志存证
    
    private Date createdAt;
    private Date updatedAt;
}
