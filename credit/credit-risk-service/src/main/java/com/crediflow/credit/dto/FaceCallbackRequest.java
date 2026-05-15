package com.crediflow.credit.dto;

import lombok.Data;

/**
 * 人脸活体检测回调入参（由三方或内部网关 POST JSON 解析而来）。
 * 该类使用@Data注解，表示这是一个Lombok的数据类，会自动生成getter、setter、toString等方法。
 */
@Data
public class FaceCallbackRequest {
    // 应用ID，用于标识不同的应用
    private String applicationId;
    // 用户ID，用于标识具体的用户
    private Long userId;
    // 检测是否通过，true表示通过，false表示未通过
    private boolean passed;
    // 错误信息，当检测未通过时，可能包含具体的错误原因
    private String errorMessage;
}
