package com.crediflow.loan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crediflow.loan.entity.LoanApplication;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoanApplicationMapper extends BaseMapper<LoanApplication> {
}
