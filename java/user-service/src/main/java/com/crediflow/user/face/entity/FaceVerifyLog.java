package com.crediflow.user.face.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 人脸核验流水。{@code providerBizNo} 全局唯一，作为回调对单依据。
 */
@Data
@TableName("cf_face_verify_log")
public class FaceVerifyLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private String providerId;
    private String providerBizNo;
    private String providerTxnNo;

    /** PROCESSING / SUCCESS / FAILED */
    private String status;
    private String failureCode;
    private String failureReasonInternal;

    private String payloadDigest;
    private Date callbackReceivedAt;
    private Integer durationMs;

    /** MOCK / WHITELIST / HTTP / BACKDOOR */
    private String channel;

    private Date createdAt;
}
