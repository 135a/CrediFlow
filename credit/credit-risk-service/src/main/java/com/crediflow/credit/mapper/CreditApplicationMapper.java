package com.crediflow.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crediflow.credit.entity.CreditApplication;
import org.apache.ibatis.annotations.Mapper;

/**
 * 信贷申请数据访问层接口
 * 继承MyBatis-Plus的基础Mapper接口，提供基础的CRUD操作
 * 使用@Mapper注解标记为MyBatis的Mapper接口
 *
 * @param <T> 泛型参数，这里指定为CreditApplication实体类
 */
@Mapper
public interface CreditApplicationMapper extends BaseMapper<CreditApplication> {
    // 该接口继承自BaseMapper，无需额外定义方法即可获得基础的CRUD功能
    // BaseMapper提供了丰富的数据库操作方法，如：insert, updateById, selectById, deleteById等
    // 实际使用时，MyBatis-Plus会自动实现这些方法，无需手动编写SQL
}
