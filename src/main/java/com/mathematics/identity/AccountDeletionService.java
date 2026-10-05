package com.mathematics.identity;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mathematics.admin.AuditRepository;
import com.mathematics.support.ApiException;

/**
 * 账号注销。面向未成年人的产品必须提供自助注销入口（《未成年人网络保护条例》），
 * 注销走匿名化：作答统计保留但不再能关联到任何联系方式。
 */
@Service
public class AccountDeletionService {

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordResetRepository resetCodes;
    private final AuditRepository audit;
    private final PasswordEncoder passwordEncoder;

    public AccountDeletionService(UserRepository users, AuthTokenRepository tokens,
                                  PasswordResetRepository resetCodes, AuditRepository audit,
                                  PasswordEncoder passwordEncoder) {
        this.users = users;
        this.tokens = tokens;
        this.resetCodes = resetCodes;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 要求再输一次密码：手机被别人拿着、令牌没过期时，不该一点就把账号毁掉。
     * 管理员不能自助注销——最后一个管理员注销掉，后台就没人能进了，先降级再注销。
     */
    @Transactional
    public void delete(long userId, String password) {
        UserRow user = users.findById(userId).orElseThrow(() -> ApiException.notFound("用户不存在"));
        if ("ADMIN".equals(user.role())) {
            throw ApiException.forbidden("管理员账号不能自助注销，请先让其他管理员撤销你的管理权限");
        }
        // 不用 401：前端把 401 当作登录态失效去刷新令牌，密码输错不是那回事
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw ApiException.invalid("密码不正确");
        }
        users.anonymize(userId);
        tokens.revokeAllForUser(userId);
        resetCodes.invalidateOutstanding(userId);
        audit.append(userId, "ACCOUNT_DELETE", "USER", userId, null);
    }
}
