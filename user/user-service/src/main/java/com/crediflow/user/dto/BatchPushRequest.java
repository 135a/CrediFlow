package com.crediflow.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public class BatchPushRequest {

    private LocalDateTime batchTime;
    private List<String> types;
    private String triggerSource;

    public LocalDateTime getBatchTime() {
        return batchTime;
    }

    public void setBatchTime(LocalDateTime batchTime) {
        this.batchTime = batchTime;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    public String getTriggerSource() {
        return triggerSource;
    }

    public void setTriggerSource(String triggerSource) {
        this.triggerSource = triggerSource;
    }
}
