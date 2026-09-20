package com.mathematics.identity;

import com.mathematics.support.ApiException;

/**
 * 控制器参数。匿名访问时注入 {@link #anonymous()}，需要登录的接口调用 {@link #requireId()}。
 */
public record CurrentUser(Long id, String role, boolean practiceMode) {

    private static final CurrentUser ANONYMOUS = new CurrentUser(null, null, false);

    public static CurrentUser anonymous() {
        return ANONYMOUS;
    }

    public boolean loggedIn() {
        return id != null;
    }

    public long requireId() {
        if (id == null) {
            throw ApiException.unauthorized();
        }
        return id;
    }

    public void requireAdmin() {
        requireId();
        if (!"ADMIN".equals(role)) {
            throw ApiException.forbidden("需要管理员权限");
        }
    }
}
