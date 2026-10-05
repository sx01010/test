package com.mathematics.guard;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 固定窗口限流：{@link #window} 内同一个 key 最多放行 {@link #limit} 次，超出返回 429。
 *
 * <p>可重复标注，同一接口同时按 IP 和按账号限流时，每条规则独立计数，任一条超限即拒绝。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /** 规则名，同时是计数 key 的前缀。不同接口别重名，否则会共用额度。 */
    String name();

    int limit();

    /** ISO-8601 时长，如 {@code PT1M}、{@code PT1H}。 */
    String window() default "PT1M";

    By by() default By.IP;

    /**
     * {@link By#EXPRESSION} 时使用的 SpEL，按方法参数名取值，如 {@code #request.account()}。
     * 求值结果为空时退回按 IP 计数，免得空 key 让所有人共享一个桶。
     */
    String key() default "";

    enum By {
        /** 匿名接口：注册、登录、找回密码。 */
        IP,
        /** 已登录按用户，未登录退回 IP。 */
        USER_OR_IP,
        /** 按请求里的某个值，比如登录账号。 */
        EXPRESSION
    }
}
