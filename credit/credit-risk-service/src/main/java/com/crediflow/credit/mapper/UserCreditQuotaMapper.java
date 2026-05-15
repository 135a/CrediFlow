package com.crediflow.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crediflow.credit.entity.UserCreditQuota;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户信用额度数据访问接口
 * 该接口继承自BaseMapper，提供了对UserCreditQuota实体类的基本数据库操作
 * 通过@Mapper注解标记为MyBatis的数据访问层接口
 */
@Mapper
public interface UserCreditQuotaMapper extends BaseMapper<UserCreditQuota> {
    // 接口内容为空，因为BaseMapper已经提供了基本的CRUD操作方法
    // 包括：插入、删除、更新、根据ID查询、条件查询等常用数据库操作
}
