package com.crediflow.user.realname.util;

import java.util.regex.Pattern;

public final class IdCardValidator {

    private static final Pattern ID18 = Pattern.compile("^\\d{17}[\\dXx]$");

    private IdCardValidator() {}

    public static boolean isValid18(String id) {
        if (id == null || id.length() != 18 || !ID18.matcher(id).matches()) {
            return false;
        }
        return verifyChecksum(id);
    }

    private static boolean verifyChecksum(String id) {
        int[] w = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] m = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (id.charAt(i) - '0') * w[i];
        }
        char expected = m[sum % 11];
        char actual = Character.toUpperCase(id.charAt(17));
        return actual == expected;
    }
}
