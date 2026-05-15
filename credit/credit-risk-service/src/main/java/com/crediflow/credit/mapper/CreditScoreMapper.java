package com.crediflow.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crediflow.credit.entity.CreditScore;
import org.apache.ibatis.annotations.Mapper;

/**
 * 信用评分数据访问接口
 * 该接口继承自BaseMapper，提供了对CreditScore实体的基本数据库操作能力
 * 使用@Mapper注解标记为MyBatis的数据访问接口
 */
@Mapper
public interface CreditScoreMapper extends BaseMapper<CreditScore> {
    // 接口体为空，因为继承自BaseMapper的所有方法已经可以直接使用
    // BaseMapper提供了基本的CRUD操作，包括插入、删除、更新和查询等功能
}
