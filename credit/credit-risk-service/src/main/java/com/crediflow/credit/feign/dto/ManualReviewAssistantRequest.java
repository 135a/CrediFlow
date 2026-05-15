package com.crediflow.credit.feign.dto;

import lombok.Data;

/**
 * 手工审核助手请求类
 * 用于封装手工审核功能所需的请求参数
 */
@Data  // Lombok注解，自动生成getter、setter等方法
public class ManualReviewAssistantRequest {
    private Long userId;          // 用户ID，标识发起请求的用户
    private String sceneType;     // 场景类型，标识审核发生的具体场景
    private ManualReviewScoreDetail scoreDetail;  // 手工审核分数详情对象，包含具体的分数信息
}
