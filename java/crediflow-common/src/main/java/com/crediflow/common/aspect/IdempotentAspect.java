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

@Aspect
@Component
public class IdempotentAspect {

    @Autowired
    private IdempotentUtils idempotentUtils;

    private ExpressionParser parser = new SpelExpressionParser();
    private DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String keyExpression = idempotent.key();
        String lockKey = generateKeyBySpEL(keyExpression, joinPoint);
        
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Idempotent key cannot be empty");
        }

        boolean locked = idempotentUtils.tryLock(lockKey, idempotent.waitTime(), idempotent.leaseTime(), TimeUnit.SECONDS);
        if (!locked) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请勿重复提交");
        }

        try {
            return joinPoint.proceed();
        } finally {
            // 注意：某些业务场景如果希望绝对防止一段时间内的重放，这里可以选择不释放锁，让其自然过期。
            // 这里出于资源释放角度考虑释放锁，若需防并发，此逻辑已足够。
            // 针对真实业务，可能需要对比 DB 状态，如果成功了就不解锁或留标志。
            // idempotentUtils.unlock(lockKey); 
        }
    }

    private String generateKeyBySpEL(String spELString, ProceedingJoinPoint joinPoint) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        String[] params = discoverer.getParameterNames(method);
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                context.setVariable(params[i], args[i]);
            }
        }
        return parser.parseExpression(spELString).getValue(context, String.class);
    }
}
