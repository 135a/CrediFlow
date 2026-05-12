package com.crediflow.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.user.entity.UserKyc;

public interface UserKycService extends IService<UserKyc> {
    UserKyc getByUserId(Long userId);
}
