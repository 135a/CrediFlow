package com.crediflow.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 贷款合同实体类
 * 使用@Data注解自动生成getter、setter、toString等方法
 * 使用@TableName注解指定对应的数据库表名为"cf_loan_contract"
 */
@Data
@TableName("cf_loan_contract")
public class LoanContract {
    /**
     * 合同ID（分布式 ID，满足分片与跨服务引用）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 合同编号
     */
    private String contractNo;
    /**
     * 申请ID
     */
    private Long applicationId;
    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 合同类型
     * 可能值为：CREDIT_CONTRACT(信用合同)或LOAN_CONTRACT(贷款合同)
     */
    private String contractType; // CREDIT_CONTRACT or LOAN_CONTRACT
    /**
     * 合同URL地址
     */
    private String contractUrl;
    /**
     * 合同状态
     * 可能值为：INIT(初始状态)、SIGNED(已签署)、EXPIRED(已过期)
     */
    private String status; // INIT, SIGNED, EXPIRED
    /**
     * 创建时间
     */
    private Date createdAt;
    /**
     * 更新时间
     */
    private Date updatedAt;
}
