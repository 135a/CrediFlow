package com.crediflow.credit.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReviewSceneType {
    CREDIT("CREDIT"),
    LOAN("LOAN");

    @EnumValue
    private final String code;

    public static ReviewSceneType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return CREDIT;
        }
        for (ReviewSceneType t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        return CREDIT;
    }
}
