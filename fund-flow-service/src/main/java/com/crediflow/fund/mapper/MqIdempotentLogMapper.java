package com.crediflow.fund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crediflow.fund.entity.MqIdempotentLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MqIdempotentLogMapper extends BaseMapper<MqIdempotentLog> {
}
