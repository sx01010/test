package com.mathematics.identity;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mathematics.config.AppProperties;
import com.mathematics.identity.IdentityDtos.ResetPasswordRequest;
import com.mathematics.identity.IdentityDtos.TokenResponse;
import com.mathematics.support.ApiException;

/**
 * R20 找回密码。
 *
 * <p>两条贯穿始终的原则：
 * <ul>
 *   <li><b>不泄露账号是否存在。</b>申请验证码永远返回同一个响应，校验失败永远返回同一句话。
 *       一个「该邮箱未注册」的提示就把接口变成了账号枚举器。</li>
 *   <li><b>验证码不出服务端。</b>只进日志，绝不进响应体——放进响应体等于任何人都能拿别人的
 *       邮箱要一个码然后直接改密码。</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String EMAIL = "EMAIL";
    private static final String PHONE = "PHONE";

    /** 码错、码过期、码用过、猜太多次、账号不存在，全都回这一句。 */
    private static final String OPAQUE_FAILURE = "验证码无效或已过期，请重新获取";

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordResetRepository resetCodes;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties.Auth config;

    public PasswordResetService(UserRepository users, AuthTokenRepository tokens,
                               PasswordResetRepository resetCodes, AuthService authService,
                               PasswordEncoder passwordEncoder, AppProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.resetCodes = resetCodes;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.config = properties.auth();
    }

    /**
     * 申请验证码。无论账号是否存在、是否还在冷却期，对外都是同一个响应。
     *
     * <p>冷却期内不签发新码却仍回成功，是刻意的：回 429 或「请稍后再试」会暴露账号存在，
     * 因为不存在的账号永远进不了冷却期。规格的「1 分钟只发一次」靠不签发来满足，
     * 不靠错误提示。
     */
    @Transactional
    public void requestCode(String account) {
        Optional<UserRow> found = users.findByAccount(account.trim());
        if (found.isEmpty()) {
            return;
        }
        UserRow user = found.get();
        if (resetCodes.issuedSince(user.id(), LocalDateTime.now().minus(config.resetCodeCooldown()))) {
            return;
        }

        resetCodes.invalidateOutstanding(user.id());
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        resetCodes.insert(user.id(), channelOf(user, account.trim()), passwordEncoder.encode(code),
                LocalDateTime.now().plus(config.resetCodeTtl()));
        deliver(user, code);
    }

    /**
     * 用验证码重置密码，成功后直接签发新令牌：用户刚证明了自己控制那个邮箱或手机号，
     * 再要求输一次密码没有意义。
     *
     * <p>失败要落库（猜错次数），所以 ApiException 不能回滚，否则次数永远加不上去——
     * 和 {@link AuthService#login} 的锁定计数是同一个坑。
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse reset(ResetPasswordRequest request) {
        UserRow user = users.findByAccount(request.account().trim())
                .orElseThrow(() -> ApiException.invalid(OPAQUE_FAILURE));
        PasswordResetRepository.Code code = resetCodes.findOutstanding(user.id())
                .orElseThrow(() -> ApiException.invalid(OPAQUE_FAILURE));

        // 过期和超次数都先作废再报错，免得这个码继续占着「当前有效码」的位置
        if (code.expiresAt().isBefore(LocalDateTime.now()) || code.attemptCount() >= config.resetMaxAttempts()) {
            resetCodes.consume(code.id());
            throw ApiException.invalid(OPAQUE_FAILURE);
        }
        if (!passwordEncoder.matches(request.code(), code.codeHash())) {
            resetCodes.recordFailedAttempt(code.id());
            throw ApiException.invalid(OPAQUE_FAILURE);
        }

        resetCodes.consume(code.id());
        // 顺带清掉锁定与失败计数：忘密码的人往往已经试错到被锁，重置完还进不来就白做了
        users.resetPassword(user.id(), passwordEncoder.encode(request.newPassword()));
        // 密码改了，旧会话必须全部失效——找回密码的常见场景就是号被别人登进去了
        tokens.revokeAllForUser(user.id());
        return authService.issuePair(user.id());
    }

    /**
     * V1 没有邮件与短信通道，验证码只能落日志。接真实通道时换掉这个方法，其余逻辑不动。
     */
    private void deliver(UserRow user, String code) {
        if (config.logResetCodes()) {
            log.info("[开发环境] 用户 {} 的找回密码验证码：{}（{} 分钟内有效）",
                    user.id(), code, config.resetCodeTtl().toMinutes());
            return;
        }
        log.warn("用户 {} 申请了找回密码验证码，但没有配置任何发送通道，验证码无法送达", user.id());
    }

    private static String channelOf(UserRow user, String account) {
        return account.equalsIgnoreCase(user.email()) ? EMAIL : PHONE;
    }
}
