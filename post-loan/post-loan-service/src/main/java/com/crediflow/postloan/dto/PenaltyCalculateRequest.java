package com.crediflow.postloan.dto;

import java.time.LocalDate;

public class PenaltyCalculateRequest {

    private LocalDate calcDate;
    private String triggerSource;

    public LocalDate getCalcDate() {
        return calcDate;
    }

    public void setCalcDate(LocalDate calcDate) {
        this.calcDate = calcDate;
    }

    public String getTriggerSource() {
        return triggerSource;
    }

    public void setTriggerSource(String triggerSource) {
        this.triggerSource = triggerSource;
    }
}
