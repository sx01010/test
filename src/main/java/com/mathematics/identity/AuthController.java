package com.mathematics.identity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.identity.IdentityDtos.LoginRequest;
import com.mathematics.identity.IdentityDtos.OkResponse;
import com.mathematics.identity.IdentityDtos.RefreshRequest;
import com.mathematics.identity.IdentityDtos.RegisterRequest;
import com.mathematics.identity.IdentityDtos.ResetCodeRequest;
import com.mathematics.identity.IdentityDtos.ResetCodeResponse;
import com.mathematics.identity.IdentityDtos.ResetPasswordRequest;
import com.mathematics.identity.IdentityDtos.TokenResponse;

import jakarta.validation.Valid;

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
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.account(), request.password());
    }

    @PostMapping("/refresh")
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
     * 任何差别都会把这个接口变成账号枚举器。
     */
    @PostMapping("/password/reset-code")
    public ResetCodeResponse requestResetCode(@Valid @RequestBody ResetCodeRequest request) {
        passwordReset.requestCode(request.account());
        return new ResetCodeResponse(true);
    }

    @PostMapping("/password/reset")
    public TokenResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return passwordReset.reset(request);
    }
}
