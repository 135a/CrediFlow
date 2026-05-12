package com.crediflow.credit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.crediflow.credit.entity.CreditResult;

public interface CreditService extends IService<CreditResult> {
    CreditResult applyCredit(Long userId);
    CreditResult getActiveCredit(Long userId);
}
