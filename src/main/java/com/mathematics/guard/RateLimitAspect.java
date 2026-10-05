package com.mathematics.guard;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import com.mathematics.config.AppProperties;
import com.mathematics.identity.CurrentUser;
import com.mathematics.identity.CurrentUserResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@link RateLimit} 的执行者。计数发生在进入控制器方法之前，被拒的请求不会碰数据库。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitAspect {

    private final RateLimiter limiter;
    private final CurrentUserResolver currentUsers;
    private final AppProperties.RateLimit config;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    public RateLimitAspect(RateLimiter limiter, CurrentUserResolver currentUsers, AppProperties properties) {
        this.limiter = limiter;
        this.currentUsers = currentUsers;
        this.config = properties.rateLimit();
    }

    @Around("@annotation(com.mathematics.guard.RateLimit) || @annotation(com.mathematics.guard.RateLimits)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!config.enabled()) {
            return joinPoint.proceed();
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Set<RateLimit> rules = AnnotatedElementUtils.findMergedRepeatableAnnotations(method, RateLimit.class);
        HttpServletRequest request = Requests.current();
        for (RateLimit rule : rules) {
            String subject = subject(rule, request, method, joinPoint);
            RateLimiter.Decision decision = limiter.acquire(rule.name() + ':' + subject, rule.limit(),
                    Duration.parse(rule.window()));
            if (!decision.allowed()) {
                throw new RateLimitedException(decision.retryAfter());
            }
        }
        return joinPoint.proceed();
    }

    private String subject(RateLimit rule, HttpServletRequest request, Method method, ProceedingJoinPoint joinPoint) {
        return switch (rule.by()) {
            case IP -> "ip:" + clientIp(request);
            case USER_OR_IP -> {
                CurrentUser user = currentUsers.resolve(request);
                yield user.loggedIn() ? "u:" + user.id() : "ip:" + clientIp(request);
            }
            case EXPRESSION -> {
                Object value = evaluate(rule.key(), method, joinPoint);
                String text = value == null ? "" : value.toString().trim().toLowerCase();
                yield text.isEmpty() ? "ip:" + clientIp(request) : "k:" + text;
            }
        };
    }

    private Object evaluate(String key, Method method, ProceedingJoinPoint joinPoint) {
        Expression expression = expressions.computeIfAbsent(key, parser::parseExpression);
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNames);
        return expression.getValue(context);
    }

    /**
     * 只有明确声明了前面有可信反向代理，才读 X-Forwarded-For。直连部署时这个头谁都能伪造，
     * 读它等于让攻击者自己挑一个 IP 来绕过限流。
     */
    private String clientIp(HttpServletRequest request) {
        if (config.trustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
