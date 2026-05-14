package com.crediflow.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crediflow.credit.entity.CreditScore;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CreditScoreMapper extends BaseMapper<CreditScore> {
}
