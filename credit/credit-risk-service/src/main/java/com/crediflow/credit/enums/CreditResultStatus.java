package com.crediflow.credit.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum CreditResultStatus {
    ACTIVE("ACTIVE", "激活"),
    FROZEN("FROZEN", "冻结"),
    EXPIRED("EXPIRED", "过期");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    CreditResultStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static CreditResultStatus fromCode(String code) {
        for (CreditResultStatus v : values()) {
            if (v.code.equals(code)) return v;
        }
        throw new IllegalArgumentException("Unknown CreditResultStatus: " + code);
    }
}
