package com.crediflow.user.realname.provider;

import com.crediflow.user.realname.model.RealnameVerifyCommand;
import com.crediflow.user.realname.model.RealnameVerifyResult;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockRealnameProvider implements RealnameProvider {

    @Override
    public RealnameVerifyResult verify(RealnameVerifyCommand command) {
        return RealnameVerifyResult.success("MOCK-" + UUID.randomUUID());
    }
}
