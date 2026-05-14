package com.crediflow.user.realname.model;

public record RealnameVerifyCommand(long userId, String realName, String idCardNo) {}
