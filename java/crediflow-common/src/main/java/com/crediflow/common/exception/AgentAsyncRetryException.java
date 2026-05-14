package com.crediflow.common.exception;

/**
 * 最终一致性 / 异步补偿重试异常
 * 当 Agent 间调用网络抖动或处理超时时抛出，用于触发上层业务状态机进行重试，而不是让整个大事务回滚。
 */
public class AgentAsyncRetryException extends RuntimeException {

    private final String agentName;

    public AgentAsyncRetryException(String message, String agentName) {
        super(message);
        this.agentName = agentName;
    }

    public AgentAsyncRetryException(String message, Throwable cause, String agentName) {
        super(message, cause);
        this.agentName = agentName;
    }

    public String getAgentName() {
        return agentName;
    }
}
