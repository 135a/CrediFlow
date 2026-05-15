package com.crediflow.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crediflow.contract.entity.LoanReceipt;
import org.apache.ibatis.annotations.Mapper;

/**
 * 贷款收据映射器接口
 * 继承自BaseMapper，提供贷款收据(LoanReceipt)的基础数据库操作
 * 使用@Mapper注解标记为MyBatis的映射接口
 */
@Mapper
public interface LoanReceiptMapper extends BaseMapper<LoanReceipt> {}
