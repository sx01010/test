package com.mathematics.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.guard.RequireLogin;
import com.mathematics.identity.IdentityDtos.UpdateProfileRequest;
import com.mathematics.identity.IdentityDtos.UserProfile;
import com.mathematics.support.ApiException;

import jakarta.validation.Valid;

@RestController
@RequireLogin
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public UserProfile me(CurrentUser me) {
        return load(me.requireId());
    }

    @PatchMapping
    public UserProfile update(CurrentUser me, @Valid @RequestBody UpdateProfileRequest request) {
        long userId = me.requireId();
        users.updateProfile(userId, trimToNull(request.nickname()), request.avatarPreset(), request.practiceMode());
        return load(userId);
    }

    private UserProfile load(long userId) {
        return users.findById(userId).map(UserProfile::of)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
