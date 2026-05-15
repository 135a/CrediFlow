package com.crediflow.credit.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.credit.dto.CreditApplicationResultView;
import com.crediflow.credit.dto.CreditApplicationStatusView;
import com.crediflow.credit.entity.CreditApplication;

import java.util.Date;

public interface CreditApplicationService extends IService<CreditApplication> {

    CreditApplicationStatusView getLastApplicationStatus(Long userId);

    CreditApplicationResultView getLastApplicationResult(Long userId);

    Page<CreditApplication> listApplications(long current, long size, Date startTime, Date endTime, String phone);
}
