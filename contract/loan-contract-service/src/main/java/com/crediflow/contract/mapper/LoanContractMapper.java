package com.crediflow.contract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crediflow.contract.entity.LoanContract;
import org.apache.ibatis.annotations.Mapper;

/**
 * 贷款合同映射接口
 * 继承BaseMapper接口，提供基础的数据库操作功能
 * 使用@Mapper注解标记为MyBatis的映射接口
 *
 * @param <LoanContract> 泛型参数，指定实体类为LoanContract
 */
@Mapper
public interface LoanContractMapper extends BaseMapper<LoanContract> {
}
