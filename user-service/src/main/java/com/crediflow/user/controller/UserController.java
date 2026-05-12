package com.crediflow.user.controller;

import com.crediflow.common.auth.annotation.IgnoreAuth;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import com.crediflow.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/user")
public class UserController {

    @Autowired
    private UserService userService;

    @IgnoreAuth
    @PostMapping("/register")
    public Result<String> register(@RequestParam String phone, 
                                   @RequestParam String password,
                                   @RequestParam String confirmPassword,
                                   @RequestParam String smsCode) {
        String token = userService.register(phone, password, confirmPassword, smsCode);
        return Result.success(token);
    }

    @IgnoreAuth
    @PostMapping("/login")
    public Result<String> login(@RequestParam String phone, @RequestParam String password) {
        String token = userService.login(phone, password);
        return Result.success(token);
    }

    @IgnoreAuth
    @PostMapping("/auth/sms")
    public Result<?> smsAuth() {
        // Task 7.2 为短信/人脸认证预留接口与开关（未开通时明确错误语义）
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "短信认证功能暂未开通");
    }

    @IgnoreAuth
    @PostMapping("/auth/face")
    public Result<?> faceAuth() {
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "人脸识别功能暂未开通");
    }
}
