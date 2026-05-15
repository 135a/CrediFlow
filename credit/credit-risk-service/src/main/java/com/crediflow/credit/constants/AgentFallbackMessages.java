package com.crediflow.credit.constants;

/**
 * Agent 降级时的固定文案常量，集中管理避免分散的魔法字符串。
 */
public final class AgentFallbackMessages {

    private AgentFallbackMessages() {}

    public static final String RISK_DETAIL_TIMEOUT = "获取风险明细超时";
    public static final String SUGGESTION_REJECT = "建议拒绝";

    public static final String USER_SAFE_INSIGHT = "综合评估未通过，请保持良好信用记录后重试";
    public static final String ADMIN_INSIGHT_TIMEOUT = "Agent超时未返回洞察";
    public static final String ACTIONABLE_ADVICE = "建议三个月后再试";
}
