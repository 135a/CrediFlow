package com.crediflow.credit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.common.web.Result;
import com.crediflow.credit.constants.CreditQueryConstants;
import com.crediflow.credit.dto.CreditApplicationResultView;
import com.crediflow.credit.dto.CreditApplicationStatusView;
import com.crediflow.credit.entity.CreditApplication;
import com.crediflow.credit.feign.UserClient;
import com.crediflow.credit.mapper.CreditApplicationMapper;
import com.crediflow.credit.service.CreditApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class CreditApplicationServiceImpl extends ServiceImpl<CreditApplicationMapper, CreditApplication>
        implements CreditApplicationService {

    @Autowired
    private UserClient userClient;

    @Override
    public CreditApplicationStatusView getLastApplicationStatus(Long userId) {
        LambdaQueryWrapper<CreditApplication> query = new LambdaQueryWrapper<>();
        query.eq(CreditApplication::getUserId, userId)
             .orderByDesc(CreditApplication::getCreatedAt)
             .last("LIMIT 1");

        CreditApplication app = this.getOne(query);
        CreditApplicationStatusView view = new CreditApplicationStatusView();
        if (app != null) {
            view.setStatus(app.getStatus() != null ? app.getStatus().getCode() : null);
            view.setApplicationId(app.getId());
            view.setSecondaryFaceRequired(app.getSecondaryFaceRequired());
        } else {
            view.setStatus(CreditQueryConstants.NOT_APPLIED);
        }
        return view;
    }

    @Override
    public CreditApplicationResultView getLastApplicationResult(Long userId) {
        LambdaQueryWrapper<CreditApplication> query = new LambdaQueryWrapper<>();
        query.eq(CreditApplication::getUserId, userId)
             .orderByDesc(CreditApplication::getCreatedAt)
             .last("LIMIT 1");

        CreditApplication app = this.getOne(query);
        CreditApplicationResultView view = new CreditApplicationResultView();
        if (app != null) {
            view.setStatus(app.getStatus() != null ? app.getStatus().getCode() : null);
            view.setAuditReason(app.getAuditReason());
            view.setUserSafeInsight(app.getUserSafeInsight() != null
                    ? app.getUserSafeInsight()
                    : "综合评估未通过，请保持良好信用记录后重试");
        }
        return view;
    }

    @Override
    public Page<CreditApplication> listApplications(long current, long size, Date startTime, Date endTime, String phone) {
        LambdaQueryWrapper<CreditApplication> queryWrapper = new LambdaQueryWrapper<>();

        if (phone != null && !phone.trim().isEmpty()) {
            Result<Long> userRes = userClient.getUserIdByPhone(phone);
            if (userRes == null || userRes.getData() == null) {
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

        return this.page(new Page<>(current, size), queryWrapper);
    }
}
