package com.crediflow.user.realname.util;

public final class IdCardMask {

    private IdCardMask() {}

    /** 展示前 4 + 后 4，中间掩码 */
    public static String mask18(String id18) {
        if (id18 == null || id18.length() < 8) {
            return "****";
        }
        if (id18.length() == 18) {
            return id18.substring(0, 4) + "**********" + id18.substring(14);
        }
        return id18.charAt(0) + "****" + id18.substring(id18.length() - 1);
    }
}
