package com.crediflow.user.realname.signature;

import com.crediflow.user.realname.model.RealnameVerifyCommand;
import org.springframework.stereotype.Component;

@Component("noopRealnameSignatureStrategy")
public class NoOpRealnameSignatureStrategy implements RealnameSignatureStrategy {

    @Override
    public String sign(String bodyWithPlaceholdersResolvedExceptSignature, RealnameVerifyCommand command, long timestampMillis) {
        return "";
    }
}
