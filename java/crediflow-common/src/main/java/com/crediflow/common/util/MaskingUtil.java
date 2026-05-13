package com.crediflow.common.util;

/**
 * 对外展示用脱敏工具（卡号 / 手机号 / 身份证）。
 */
public final class MaskingUtil {

    private MaskingUtil() {}

    /** 18 位身份证：前 4 + 后 4，中间固定掩码（与历史 IdCardMask 一致）。 */
    public static String maskIdCard18(String id18) {
        if (id18 == null || id18.length() < 8) {
            return "****";
        }
        if (id18.length() == 18) {
            return id18.substring(0, 4) + "**********" + id18.substring(14);
        }
        return id18.charAt(0) + "****" + id18.substring(id18.length() - 1);
    }

    /** 银行卡：保留前 4 + 后 4，中间掩码；长度不足时退化为首尾各 1 位。 */
    public static String maskBankCard(String cardNo) {
        if (cardNo == null || cardNo.length() < 8) {
            return "****";
        }
        if (cardNo.length() <= 10) {
            return cardNo.charAt(0) + "****" + cardNo.substring(cardNo.length() - 1);
        }
        return cardNo.substring(0, 4) + "**********" + cardNo.substring(cardNo.length() - 4);
    }

    /** 大陆 11 位手机号：前三后四。 */
    public static String maskPhone11(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        if (phone.length() == 11) {
            return phone.substring(0, 3) + "****" + phone.substring(7);
        }
        return phone.charAt(0) + "****" + phone.substring(phone.length() - 1);
    }
}
