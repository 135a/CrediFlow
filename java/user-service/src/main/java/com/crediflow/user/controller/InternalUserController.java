package com.crediflow.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.common.web.Result;
import com.crediflow.user.entity.User;
import com.crediflow.user.entity.UserKyc;
import com.crediflow.user.realname.RealnameStatus;
import com.crediflow.user.service.UserKycService;
import com.crediflow.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/internal/user")
public class InternalUserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserKycService userKycService;

    @GetMapping("/by-phone")
    public Result<Long> getUserIdByPhone(@RequestParam String phone) {
        User user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user != null) {
            return Result.success(user.getId());
        }
        return Result.success(null);
    }

    /**
     * 供绑卡、授信、合同等内部服务判断用户是否已通过第三方实名核验。
     * 约定：{@code verified} 为 true 当且仅当 {@code realnameStatus == VERIFIED}。
     */
    @GetMapping("/kyc/realname-status")
    public Result<Map<String, Object>> realnameStatus(@RequestParam Long userId) {
        UserKyc kyc = userKycService.getByUserId(userId);
        boolean verified =
                kyc != null && RealnameStatus.VERIFIED.name().equals(kyc.getRealnameStatus());
        String status =
                kyc == null || kyc.getRealnameStatus() == null
                        ? RealnameStatus.NOT_SUBMITTED.name()
                        : kyc.getRealnameStatus();
        return Result.success(Map.of("verified", verified, "realnameStatus", status));
    }
}
