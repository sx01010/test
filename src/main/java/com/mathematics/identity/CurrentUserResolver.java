package com.mathematics.identity;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Bearer 令牌换当前用户，一个请求只查一次库。
 *
 * <p>参数解析器和切面都要知道「当前是谁」，结果挂在 request attribute 上共用，
 * 否则一个带 {@code @RequireLogin} 又带 {@code @RateLimit} 的接口要查三遍令牌表。
 */
@Component
public class CurrentUserResolver {

    private static final String BEARER = "Bearer ";
    private static final String ATTRIBUTE = CurrentUserResolver.class.getName();

    private final AuthService authService;

    public CurrentUserResolver(AuthService authService) {
        this.authService = authService;
    }

    public CurrentUser resolve(HttpServletRequest request) {
        if (request == null) {
            return CurrentUser.anonymous();
        }
        Object cached = request.getAttribute(ATTRIBUTE);
        if (cached instanceof CurrentUser user) {
            return user;
        }
        CurrentUser user = fromHeader(request.getHeader("Authorization"));
        request.setAttribute(ATTRIBUTE, user);
        return user;
    }

    private CurrentUser fromHeader(String header) {
        if (header == null || !header.startsWith(BEARER)) {
            return CurrentUser.anonymous();
        }
        String token = header.substring(BEARER.length()).trim();
        return authService.resolveAccessToken(token).orElseGet(CurrentUser::anonymous);
    }
}
