package com.crediflow.credit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 黑名单检查结果（当前桩实现恒为未命中）。
 * 该类使用Lombok注解简化代码，包含：
 * @Data - 提供getter、setter、toString等方法
 * @NoArgsConstructor - 提供无参构造函数
 * @AllArgsConstructor - 提供全参构造函数
 * @JsonInclude(JsonInclude.Include.NON_NULL) - JSON序列化时忽略null值
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BlacklistCheckResult {

    // 是否命中黑名单，布尔类型
    private boolean hit;

    // 未命中黑名单的原因代码，字符串类型
    private String reasonCode;
}
