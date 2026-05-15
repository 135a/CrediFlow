package com.crediflow.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crediflow.credit.entity.CreditResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 信贷结果映射接口
 * 该接口继承自BaseMapper，用于提供CreditResult实体的数据库操作方法
 * 使用@Mapper注解标记为MyBatis的映射接口
 */
@Mapper
public interface CreditResultMapper extends BaseMapper<CreditResult> {
}
