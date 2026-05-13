package com.crediflow.user.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** 对外 KYC 状态：不含明文证件号与内部原因码 */
public class UserKycStatusView {

    private Long userId;
    private Integer stepStatus;
    private String realnameStatus;
    private String idCardMask;
    private BigDecimal monthlyIncome;
    private Date birthDate;
    private String residence;
    private String occupation;
    private String realName;
    private Integer age;
    private String paymentMethod;
    private String paymentAccount;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("stepStatus", stepStatus);
        m.put("realnameStatus", realnameStatus);
        m.put("idCardMask", idCardMask);
        m.put("monthlyIncome", monthlyIncome);
        m.put("birthDate", birthDate);
        m.put("residence", residence);
        m.put("occupation", occupation);
        m.put("realName", realName);
        m.put("age", age);
        m.put("paymentMethod", paymentMethod);
        m.put("paymentAccount", paymentAccount);
        return m;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getStepStatus() {
        return stepStatus;
    }

    public void setStepStatus(Integer stepStatus) {
        this.stepStatus = stepStatus;
    }

    public String getRealnameStatus() {
        return realnameStatus;
    }

    public void setRealnameStatus(String realnameStatus) {
        this.realnameStatus = realnameStatus;
    }

    public String getIdCardMask() {
        return idCardMask;
    }

    public void setIdCardMask(String idCardMask) {
        this.idCardMask = idCardMask;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public void setMonthlyIncome(BigDecimal monthlyIncome) {
        this.monthlyIncome = monthlyIncome;
    }

    public Date getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(Date birthDate) {
        this.birthDate = birthDate;
    }

    public String getResidence() {
        return residence;
    }

    public void setResidence(String residence) {
        this.residence = residence;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentAccount() {
        return paymentAccount;
    }

    public void setPaymentAccount(String paymentAccount) {
        this.paymentAccount = paymentAccount;
    }
}
