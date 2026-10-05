package com.mathematics.identity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.guard.RateLimit;
import com.mathematics.guard.RateLimit.By;
import com.mathematics.identity.IdentityDtos.LoginRequest;
import com.mathematics.identity.IdentityDtos.OkResponse;
import com.mathematics.identity.IdentityDtos.RefreshRequest;
import com.mathematics.identity.IdentityDtos.RegisterRequest;
import com.mathematics.identity.IdentityDtos.ResetCodeRequest;
import com.mathematics.identity.IdentityDtos.ResetCodeResponse;
import com.mathematics.identity.IdentityDtos.ResetPasswordRequest;
import com.mathematics.identity.IdentityDtos.TokenResponse;

import jakarta.validation.Valid;

/**
 * 匿名接口全部限流。按账号计数的 key 取自请求体里用户填的字符串，不管这个账号存不存在都同样计数，
 * 所以 429 不会泄露账号是否注册过。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordReset;

    public AuthController(AuthService authService, PasswordResetService passwordReset) {
        this.authService = authService;
        this.passwordReset = passwordReset;
    }

    @PostMapping("/register")
    @RateLimit(name = "register-ip", limit = 10, window = "PT1H")
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** 账号锁定只防单个账号被猜；按 IP 限流防的是一个 IP 轮着猜很多账号。 */
    @PostMapping("/login")
    @RateLimit(name = "login-ip", limit = 30, window = "PT1M")
    @RateLimit(name = "login-account", limit = 10, window = "PT5M", by = By.EXPRESSION, key = "#request.account()")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.account(), request.password());
    }

    @PostMapping("/refresh")
    @RateLimit(name = "refresh-ip", limit = 60, window = "PT1M")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public OkResponse logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return new OkResponse(true);
    }

    /**
     * R20：无论账号是否存在、是否还在 1 分钟冷却期内，都回同一个响应。
     * 任何差别都会把这个接口变成账号枚举器。按 IP 限流防的是拿一个 IP 给大量邮箱轰炸验证码。
     */
    @PostMapping("/password/reset-code")
    @RateLimit(name = "reset-code-ip", limit = 5, window = "PT10M")
    public ResetCodeResponse requestResetCode(@Valid @RequestBody ResetCodeRequest request) {
        passwordReset.requestCode(request.account());
        return new ResetCodeResponse(true);
    }

    @PostMapping("/password/reset")
    @RateLimit(name = "reset-ip", limit = 20, window = "PT10M")
    @RateLimit(name = "reset-account", limit = 10, window = "PT10M", by = By.EXPRESSION, key = "#request.account()")
    public TokenResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return passwordReset.reset(request);
    }
}
