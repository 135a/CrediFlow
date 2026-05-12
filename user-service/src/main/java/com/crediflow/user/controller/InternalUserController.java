package com.crediflow.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crediflow.common.web.Result;
import com.crediflow.user.entity.User;
import com.crediflow.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/user")
public class InternalUserController {

    @Autowired
    private UserService userService;

    @GetMapping("/by-phone")
    public Result<Long> getUserIdByPhone(@RequestParam String phone) {
        User user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user != null) {
            return Result.success(user.getId());
        }
        return Result.success(null);
    }
}
