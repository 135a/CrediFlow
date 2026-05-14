package com.crediflow.user.eligibility.policy;

import com.crediflow.user.eligibility.config.KycEligibilityProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;

/**
 * 18–55 年龄合规判定。服务端从身份证第 7–14 位解析出生日期，禁止信任前端传入。
 */
@Component
public class AgeRangePolicy {

    private final KycEligibilityProperties properties;

    public AgeRangePolicy(KycEligibilityProperties properties) {
        this.properties = properties;
    }

    public Result evaluate(String idCardNo, LocalDate now) {
        LocalDate birth = parseBirth(idCardNo);
        if (birth == null) {
            return new Result(-1, false);
        }
        int age = Period.between(birth, now).getYears();
        int min = properties.getAge().getMin();
        int max = properties.getAge().getMax();
        boolean pass = age >= min && age <= max;
        return new Result(age, pass);
    }

    private LocalDate parseBirth(String idCardNo) {
        if (idCardNo == null || idCardNo.length() != 18) {
            return null;
        }
        try {
            int y = Integer.parseInt(idCardNo.substring(6, 10));
            int m = Integer.parseInt(idCardNo.substring(10, 12));
            int d = Integer.parseInt(idCardNo.substring(12, 14));
            return LocalDate.of(y, m, d);
        } catch (Exception ignored) {
            return null;
        }
    }

    public record Result(int age, boolean pass) {}
}
