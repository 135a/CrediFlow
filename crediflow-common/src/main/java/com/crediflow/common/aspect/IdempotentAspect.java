package com.crediflow.common.aspect;

import com.crediflow.common.annotation.Idempotent;
import com.crediflow.common.exception.BusinessException;
import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.utils.IdempotentUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 幂切面处理类，用于处理带有@Idempotent注解的方法
 * 通过AOP实现幂等性控制，防止重复提交
 */
@Aspect
@Component
public class IdempotentAspect {

    @Autowired
    private IdempotentUtils idempotentUtils;

    // SpEL表达式解析器，用于解析表达式
    private ExpressionParser parser = new SpelExpressionParser();
    // 参数名发现器，用于获取方法参数名
    private DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    /**
     * 环绕通知，处理@Idempotent注解标注的方法
     * @param joinPoint 连接点，可以获取方法信息
     * @param idempotent 幂等性注解信息
     * @return 方法执行结果
     * @throws Throwable 方法执行可能抛出的异常
     */
    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 获取注解中定义的key表达式
        String keyExpression = idempotent.key();
        // 根据SpEL表达式和参数生成实际的key
        String lockKey = generateKeyBySpEL(keyExpression, joinPoint);
        
        // 校验key是否为空
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Idempotent key cannot be empty");
        }

        // 尝试获取分布式锁
        // 尝试获取分布式锁，使用idempotent工具类
        // 参数说明：
        // lockKey: 锁的键，用于标识锁的唯一性
        // idempotent.waitTime(): 等待获取锁的时间，单位秒
        // idempotent.leaseTime(): 锁的持有时间，单位秒
        // TimeUnit.SECONDS: 时间单位，秒
        boolean locked = idempotentUtils.tryLock(lockKey, idempotent.waitTime(), idempotent.leaseTime(), TimeUnit.SECONDS);
        // 如果获取锁失败，抛出异常，防止重复提交
        if (!locked) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请勿重复提交");
        }

        try {
            // 执行被拦截的方法
            return joinPoint.proceed();
        } finally {
            // 注意：某些业务场景如果希望绝对防止一段时间内的重放，这里可以选择不释放锁，让其自然过期。
            // 这里出于资源释放角度考虑释放锁，若需防并发，此逻辑已足够。
            // 针对真实业务，可能需要对比 DB 状态，如果成功了就不解锁或留标志。
            // idempotentUtils.unlock(lockKey);
            // 注意：某些业务场景如果希望绝对防止一段时间内的重放，这里可以选择不释放锁，让其自然过期。
            // 这里出于资源释放角度考虑释放锁，若需防并发，此逻辑已足够。
        }
    }

    /**
     * 根据SpEL表达式和方法参数生成实际的key
     * @param spELString SpEL表达式字符串
     * @param joinPoint 连接点，可以获取方法参数等信息
     * @return 解析后的key字符串
     */
    private String generateKeyBySpEL(String spELString, ProceedingJoinPoint joinPoint) {
        // 获取方法签名
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        // 获取方法对象
        Method method = methodSignature.getMethod();
        // 获取方法参数名
        String[] params = discoverer.getParameterNames(method);
        // 获取方法参数值
        Object[] args = joinPoint.getArgs();

        // 创建SpEL上下文
        EvaluationContext context = new StandardEvaluationContext();
        // 将参数名和参数值设置到上下文中
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                context.setVariable(params[i], args[i]);
            }
        }
        // 解析表达式并返回结果
        return parser.parseExpression(spELString).getValue(context, String.class);
    }
}
