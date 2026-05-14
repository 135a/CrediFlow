package com.crediflow.user.realname.service;

import com.crediflow.user.realname.config.RealnameProperties;
import com.crediflow.user.realname.model.RealnameVerifyCommand;
import com.crediflow.user.realname.model.RealnameVerifyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RealnameAuditService {

    private static final Logger log = LoggerFactory.getLogger(RealnameAuditService.class);

    public void record(
            RealnameProperties properties,
            RealnameVerifyCommand cmd,
            RealnameVerifyResult result,
            long durationMs,
            String channel) {
        String outcome;
        if (result.retryable()) {
            outcome = "RETRYABLE";
        } else if (result.terminalFailure()) {
            outcome = "FAILED";
        } else if (result.effectiveSuccess()) {
            outcome = "SUCCESS";
        } else {
            outcome = "UNKNOWN";
        }
        String txn = result.providerTxnNo() == null ? "" : result.providerTxnNo();
        log.info(
                "AUDIT realname userId={} channel={} mock={} outcome={} txn={} durationMs={} internalCode={}",
                cmd.userId(),
                channel,
                properties.isMockSuccess(),
                outcome,
                txn,
                durationMs,
                result.internalReasonCode() == null ? "" : result.internalReasonCode());
    }
}
