package com.crediflow.credit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.credit.entity.CreditApplication;

import java.util.Map;
import java.util.Date;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface CreditApplicationService extends IService<CreditApplication> {
    Map<String, Object> getLastApplicationStatus(Long userId);
    Map<String, Object> getLastApplicationResult(Long userId);
    Page<CreditApplication> listApplications(long current, long size, Date startTime, Date endTime, String phone);
}
