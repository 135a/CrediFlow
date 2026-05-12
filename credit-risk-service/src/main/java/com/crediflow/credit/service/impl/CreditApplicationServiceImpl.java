package com.crediflow.credit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.crediflow.credit.entity.CreditApplication;
import com.crediflow.credit.mapper.CreditApplicationMapper;
import com.crediflow.credit.service.CreditApplicationService;
import org.springframework.stereotype.Service;

@Service
public class CreditApplicationServiceImpl extends ServiceImpl<CreditApplicationMapper, CreditApplication> implements CreditApplicationService {
}
