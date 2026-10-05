package com.mathematics.guard;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.mathematics.identity.CurrentUser;
import com.mathematics.identity.CurrentUserResolver;

/**
 * 登录与角色校验。排在限流之前：先知道是谁，按用户限流的规则才有 key 可用。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AccessAspect {

    private final CurrentUserResolver currentUsers;

    public AccessAspect(CurrentUserResolver currentUsers) {
        this.currentUsers = currentUsers;
    }

    @Before("@within(com.mathematics.guard.RequireLogin) || @annotation(com.mathematics.guard.RequireLogin)")
    public void requireLogin() {
        current().requireId();
    }

    @Before("@within(com.mathematics.guard.RequireAdmin) || @annotation(com.mathematics.guard.RequireAdmin)")
    public void requireAdmin() {
        current().requireAdmin();
    }

    private CurrentUser current() {
        return currentUsers.resolve(Requests.current());
    }
}
