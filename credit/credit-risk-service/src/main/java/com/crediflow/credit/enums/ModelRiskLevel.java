package com.crediflow.credit.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModelRiskLevel {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH");

    private final String code;

    public static ModelRiskLevel fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ModelRiskLevel level : values()) {
            if (level.code.equals(code)) {
                return level;
            }
        }
        return null;
    }
}
