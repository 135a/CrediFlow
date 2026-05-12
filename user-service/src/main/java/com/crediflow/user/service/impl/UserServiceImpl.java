package com.crediflow.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.user.entity.User;
import com.crediflow.user.mapper.UserMapper;
import com.crediflow.user.service.UserService;
import com.crediflow.user.util.ExternalJwtUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public String register(String phone, String password, String confirmPassword, String smsCode) {
        // 1. 两次密码校验
        if (password == null || !password.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "两次输入的密码不一致");
        }

        // 2. 短信验证码校验
        // TODO: 后续需对接真实短信网关（如阿里云SMS/腾讯云短信），并从 Redis 读取缓存的真实验证码进行比对
        if (smsCode == null || smsCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "短信验证码不能为空");
        }
        if (!"123456".equals(smsCode)) { // 本地开发暂用万能验证码
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "验证码错误");
        }

        // 3. 检查手机号是否已被注册
        long count = this.count(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (count > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "手机号已注册");
        }

        User user = new User();
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(1);
        user.setCreatedAt(new Date());
        user.setUpdatedAt(new Date());
        this.save(user);

        return ExternalJwtUtils.generateToken(user.getId(), "USER");
    }

    @Override
    public String login(String phone, String password) {
        User user = this.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "手机号或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户已被冻结");
        }

        // TODO: 登录审计入库或发 MQ

        return ExternalJwtUtils.generateToken(user.getId(), "USER");
    }
}
