package com.crediflow.credit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.web.Result;
import com.crediflow.credit.entity.CreditApplication;
import com.crediflow.credit.feign.UserClient;
import com.crediflow.credit.mapper.CreditApplicationMapper;
import com.crediflow.credit.service.CreditApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class CreditApplicationServiceImpl extends ServiceImpl<CreditApplicationMapper, CreditApplication> implements CreditApplicationService {

    @Autowired
    private UserClient userClient;

    @Override
    public Map<String, Object> getLastApplicationStatus(Long userId) {
        LambdaQueryWrapper<CreditApplication> query = new LambdaQueryWrapper<>();
        query.eq(CreditApplication::getUserId, userId)
             .orderByDesc(CreditApplication::getCreatedAt)
             .last("LIMIT 1");
             
        CreditApplication app = this.getOne(query);
        Map<String, Object> map = new HashMap<>();
        if (app != null) {
            map.put("status", app.getStatus() != null ? app.getStatus().getCode() : null);
            map.put("applicationId", app.getId());
            map.put("secondaryFaceRequired", app.getSecondaryFaceRequired());
        } else {
            map.put("status", "NOT_APPLIED");
        }
        return map;
    }

    @Override
    public Map<String, Object> getLastApplicationResult(Long userId) {
        LambdaQueryWrapper<CreditApplication> query = new LambdaQueryWrapper<>();
        query.eq(CreditApplication::getUserId, userId)
             .orderByDesc(CreditApplication::getCreatedAt)
             .last("LIMIT 1");
             
        CreditApplication app = this.getOne(query);
        Map<String, Object> map = new HashMap<>();
        if (app != null) {
            map.put("status", app.getStatus() != null ? app.getStatus().getCode() : null);
            map.put("auditReason", app.getAuditReason());
            map.put("userSafeInsight", app.getUserSafeInsight() != null ? app.getUserSafeInsight() : "综合评估未通过，请保持良好信用记录后重试"); 
        }
        return map;
    }

    @Override
    public Page<CreditApplication> listApplications(long current, long size, Date startTime, Date endTime, String phone) {
        LambdaQueryWrapper<CreditApplication> queryWrapper = new LambdaQueryWrapper<>();
        
        if (phone != null && !phone.trim().isEmpty()) {
            Result<Long> userRes = userClient.getUserIdByPhone(phone);
            if (userRes == null || userRes.getData() == null) {
                // 如果找不到对应的用户，直接返回空分页
                return new Page<>(current, size);
            }
            queryWrapper.eq(CreditApplication::getUserId, userRes.getData());
        }

        if (startTime != null) {
            queryWrapper.ge(CreditApplication::getCreatedAt, startTime);
        }
        if (endTime != null) {
            queryWrapper.le(CreditApplication::getCreatedAt, endTime);
        }
        queryWrapper.orderByDesc(CreditApplication::getCreatedAt);

        Page<CreditApplication> page = new Page<>(current, size);
        return this.page(page, queryWrapper);
    }
}
