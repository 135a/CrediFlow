package com.crediflow.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crediflow.credit.entity.CreditReviewQueue;
import org.apache.ibatis.annotations.Mapper;

/**
 * 信贷审核队列Mapper接口
 * 该接口继承自BaseMapper，提供了对CreditReviewQueue实体类的基本数据库操作方法
 * 使用@Mapper注解标记为MyBatis的Mapper接口
 */
@Mapper
public interface CreditReviewQueueMapper extends BaseMapper<CreditReviewQueue> {
    // 继承BaseMapper<CreditReviewQueue>后，可以直接使用以下方法：
    // insert - 插入一条记录
    // deleteById - 根据ID删除记录
    // updateById - 根据ID更新记录
    // selectById - 根据ID查询记录
    // selectList - 条件查询记录列表
    // 以及其他MyBatis-Plus提供的基础方法
}
