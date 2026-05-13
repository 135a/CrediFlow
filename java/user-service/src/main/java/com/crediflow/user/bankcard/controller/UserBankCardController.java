package com.crediflow.user.bankcard.controller;

import com.crediflow.common.web.Result;
import com.crediflow.user.bankcard.dto.BankCardBindRequest;
import com.crediflow.user.bankcard.dto.BankCardView;
import com.crediflow.user.bankcard.service.BankCardBindingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 银行卡四要素绑卡 API。
 */
@RestController
@RequestMapping("/api/app/user/bankcard")
public class UserBankCardController {

    private final BankCardBindingService bindingService;

    public UserBankCardController(BankCardBindingService bindingService) {
        this.bindingService = bindingService;
    }

    @PostMapping("/bind")
    public Result<BankCardView> bind(@RequestParam Long userId,
                                     @RequestBody BankCardBindRequest request,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(bindingService.bind(userId,
                request == null ? null : request.getCardNo(),
                request == null ? null : request.getBankCode(),
                request == null ? null : request.getReservedPhone(),
                idempotencyKey));
    }

    @GetMapping("/list")
    public Result<List<BankCardView>> list(@RequestParam Long userId) {
        return Result.success(bindingService.list(userId));
    }

    @PostMapping("/set-primary")
    public Result<BankCardView> setPrimary(@RequestParam Long userId, @RequestBody Map<String, String> body) {
        return Result.success(bindingService.setPrimary(userId, body == null ? null : body.get("bindCardId")));
    }

    @PostMapping("/unbind")
    public Result<Void> unbind(@RequestParam Long userId, @RequestBody Map<String, String> body) {
        bindingService.unbind(userId, body == null ? null : body.get("bindCardId"));
        return Result.success();
    }
}
