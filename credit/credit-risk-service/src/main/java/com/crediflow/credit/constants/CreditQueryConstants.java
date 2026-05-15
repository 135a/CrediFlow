package com.crediflow.credit.constants;

/**
 * 查询哨兵常量：表示"无记录"的业务占位值，不属于正常状态枚举，因此不进入 CreditApplicationStatus。
 */
public final class CreditQueryConstants {

    private CreditQueryConstants() {}

    /** 用户从未申请过授信时的状态占位符 */
    public static final String NOT_APPLIED = "NOT_APPLIED";
}
