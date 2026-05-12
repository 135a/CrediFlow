package com.crediflow.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("cf_loan_contract")
public class LoanContract {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String contractNo;
    private Long applicationId;
    private Long userId;
    private String contractUrl;
    private String status;
    private Date createdAt;
    private Date updatedAt;
}
