package com.crediflow.application.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crediflow.application.entity.LocalMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LocalMessageMapper extends BaseMapper<LocalMessage> {
}
