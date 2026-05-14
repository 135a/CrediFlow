package com.crediflow.common.event;

import lombok.Data;

/**
 * 贷款生命周期消息类，用于存储和传递贷款生命周期中的各种事件信息
 * 使用@Data注解来自动生成getter、setter、toString等方法
 */
@Data
public class LoanLifecycleMessage {
    private Long loanApplicationId; // 贷款申请ID，用于标识具体的贷款申请
    private Long userId; // 用户ID，用于标识与贷款相关的用户
    private String eventType; // 事件类型，表示贷款生命周期中的具体事件类型
    private Object payload; // Can store extra JSON data if needed
}
