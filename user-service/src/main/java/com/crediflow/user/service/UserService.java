package com.crediflow.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.user.entity.User;

public interface UserService extends IService<User> {
    String register(String phone, String password);
    String login(String phone, String password);
}
