package com.crediflow.user.realname.provider;

import com.crediflow.user.realname.model.RealnameVerifyCommand;
import com.crediflow.user.realname.model.RealnameVerifyResult;

public interface RealnameProvider {

    RealnameVerifyResult verify(RealnameVerifyCommand command);
}
