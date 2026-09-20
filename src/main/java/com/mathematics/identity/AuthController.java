package com.mathematics.identity;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.identity.IdentityDtos.LoginRequest;
import com.mathematics.identity.IdentityDtos.OkResponse;
import com.mathematics.identity.IdentityDtos.RefreshRequest;
import com.mathematics.identity.IdentityDtos.RegisterRequest;
import com.mathematics.identity.IdentityDtos.TokenResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
}
