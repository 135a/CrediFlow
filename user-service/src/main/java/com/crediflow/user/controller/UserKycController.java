package com.crediflow.user.controller;

import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.web.Result;
import com.crediflow.user.entity.UserKyc;
import com.crediflow.user.feign.AgentClient;
import com.crediflow.user.service.UserKycService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/api/app/user/kyc")
public class UserKycController {

    @Autowired
    private UserKycService userKycService;

    @Autowired
    private AgentClient agentClient;

    @PostMapping("/step1")
    public Result<Void> step1(@RequestParam Long userId, @RequestBody UserKyc kycData) {
        UserKyc kyc = userKycService.getByUserId(userId);
        if (kyc == null) {
            kyc = new UserKyc();
            kyc.setUserId(userId);
            kyc.setStepStatus(0);
            kyc.setCreatedAt(new Date());
        }
        kyc.setMonthlyIncome(kycData.getMonthlyIncome());
        kyc.setBirthDate(kycData.getBirthDate());
        kyc.setResidence(kycData.getResidence());
        kyc.setOccupation(kycData.getOccupation());
        
        if (kyc.getStepStatus() < 1) {
            kyc.setStepStatus(1);
        }
        kyc.setUpdatedAt(new Date());
        userKycService.saveOrUpdate(kyc);
        return Result.success();
    }

    @PostMapping("/step2")
    public Result<Map<String, Object>> step2(@RequestParam Long userId, @RequestBody Map<String, String> request) {
        UserKyc kyc = userKycService.getByUserId(userId);
        if (kyc == null || kyc.getStepStatus() < 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请先完成步骤一");
        }
        
        String idCardBase64 = request.get("idCardBase64");
        String faceBase64 = request.get("faceBase64");
        
        // 调用 Agent OCR
        Map<String, Object> ocrRes = agentClient.extractOcr(Map.of("image_base64", idCardBase64));
        if (!"SUCCESS".equals(ocrRes.get("status"))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "身份证识别失败");
        }
        
        Map<String, Object> ocrData = (Map<String, Object>) ocrRes.get("data");
        String idCardNo = (String) ocrData.get("idCardNo");
        
        // 调用 Agent Face Verify
        Map<String, Object> faceRes = agentClient.faceVerify(Map.of("id_card_no", idCardNo, "face_image_base64", faceBase64));
        if (!"SUCCESS".equals(faceRes.get("status"))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "人脸活体认证失败");
        }
        
        kyc.setRealName((String) ocrData.get("realName"));
        kyc.setIdCardNo(idCardNo);
        kyc.setAge((Integer) ocrData.get("age"));
        kyc.setFaceVerified(true);
        
        if (kyc.getStepStatus() < 2) {
            kyc.setStepStatus(2);
        }
        kyc.setUpdatedAt(new Date());
        userKycService.updateById(kyc);
        
        return Result.success(ocrData);
    }

    @PostMapping("/step3")
    public Result<Void> step3(@RequestParam Long userId, @RequestBody UserKyc kycData) {
        UserKyc kyc = userKycService.getByUserId(userId);
        if (kyc == null || kyc.getStepStatus() < 2) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请先完成步骤二");
        }
        
        kyc.setPaymentMethod(kycData.getPaymentMethod());
        kyc.setPaymentAccount(kycData.getPaymentAccount());
        kyc.setStepStatus(3);
        kyc.setUpdatedAt(new Date());
        userKycService.updateById(kyc);
        
        return Result.success();
    }
    
    @GetMapping("/status")
    public Result<UserKyc> getStatus(@RequestParam Long userId) {
        return Result.success(userKycService.getByUserId(userId));
    }
}
