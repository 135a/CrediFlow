package com.crediflow.user.realname.util;

import com.crediflow.common.util.MaskingUtil;

public final class IdCardMask {

    private IdCardMask() {}

    /** 展示前 4 + 后 4，中间掩码 */
    public static String mask18(String id18) {
        return MaskingUtil.maskIdCard18(id18);
    }
}
