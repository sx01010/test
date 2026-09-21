package com.mathematics.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mathematics.config.AppProperties;
import com.mathematics.identity.IdentityDtos.RegisterRequest;
import com.mathematics.identity.IdentityDtos.TokenResponse;
import com.mathematics.support.ApiException;
import com.mathematics.support.ErrorCode;

@Service
public class AuthService {

    private static final String ACCESS = "ACCESS";
    private static final String REFRESH = "REFRESH";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties.Auth config;

    public AuthService(UserRepository users, AuthTokenRepository tokens,
                       PasswordEncoder passwordEncoder, AppProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.config = properties.auth();
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = blankToNull(request.email());
        String phone = blankToNull(request.phone());
        if (email == null && phone == null) {
            throw ApiException.invalid("邮箱和手机号至少填一个");
        }
        if (email != null && users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.ACCOUNT_EXISTS, "该邮箱已注册");
        }
        if (phone != null && users.existsByPhone(phone)) {
            throw new ApiException(ErrorCode.ACCOUNT_EXISTS, "该手机号已注册");
        }
        long userId = users.insert(request.nickname().trim(), email, phone,
                passwordEncoder.encode(request.password()), "USER");
        return issuePair(userId);
    }

    /**
     * 拒绝登录不能回滚：失败计数和锁定时间必须落库，否则永远锁不上。
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(String account, String password) {
        UserRow user = users.findByAccount(account.trim())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "账号或密码不正确"));

        LocalDateTime now = LocalDateTime.now();
        boolean lockExpired = user.lockedUntil() != null && !user.lockedUntil().isAfter(now);
        if (user.lockedUntil() != null && !lockExpired) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    "登录失败次数过多，账号已锁定至 " + user.lockedUntil());
        }

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            int failCount = lockExpired ? 1 : user.failCount() + 1;
            boolean shouldLock = failCount >= config.maxLoginFailures();
            users.recordLoginFailure(user.id(), shouldLock ? 0 : failCount,
                    shouldLock ? now.plus(config.lockDuration()) : null);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, shouldLock
                    ? "连续失败次数过多，账号已锁定 " + config.lockDuration().toMinutes() + " 分钟"
                    : "账号或密码不正确");
        }

        users.recordLoginSuccess(user.id());
        return issuePair(user.id());
    }

    /**
     * R02：旧 refresh token 立即作废，再签发新的一对。
     */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        String hash = hash(refreshToken);
        long userId = tokens.findActiveUserId(hash, REFRESH)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "refresh token 无效或已过期"));
        tokens.revoke(hash);
        return issuePair(userId);
    }

    @Transactional
    public void logout(String refreshToken) {
        String hash = hash(refreshToken);
        tokens.findActiveUserId(hash, REFRESH).ifPresent(tokens::revokeAllForUser);
        tokens.revoke(hash);
    }

    /**
     * 供参数解析器使用：Bearer 令牌换当前用户。令牌无效时返回空而不是抛异常，
     * 匿名可访问的接口才能照常工作。
     */
    public Optional<CurrentUser> resolveAccessToken(String accessToken) {
        return tokens.findActiveUserId(hash(accessToken), ACCESS)
                .flatMap(users::findById)
                .map(user -> new CurrentUser(user.id(), user.role(), user.practiceMode()));
    }

    /** 包内可见：{@link PasswordResetService} 重置成功后也要签发一对令牌。 */
    TokenResponse issuePair(long userId) {
        LocalDateTime now = LocalDateTime.now();
        String access = randomToken();
        String refresh = randomToken();
        tokens.insert(userId, hash(access), ACCESS, now.plus(config.accessTokenTtl()));
        tokens.insert(userId, hash(refresh), REFRESH, now.plus(config.refreshTokenTtl()));
        return new TokenResponse(userId, access, refresh);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 明文令牌不落库，只存 SHA-256。令牌本身是 256 位随机值，不需要加盐慢哈希。
     */
    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
