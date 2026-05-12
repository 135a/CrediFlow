package com.crediflow.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.user.entity.UserKyc;
import com.crediflow.user.mapper.UserKycMapper;
import com.crediflow.user.service.UserKycService;
import org.springframework.stereotype.Service;

@Service
public class UserKycServiceImpl extends ServiceImpl<UserKycMapper, UserKyc> implements UserKycService {
    @Override
    public UserKyc getByUserId(Long userId) {
        return this.getOne(new LambdaQueryWrapper<UserKyc>().eq(UserKyc::getUserId, userId));
    }
}
