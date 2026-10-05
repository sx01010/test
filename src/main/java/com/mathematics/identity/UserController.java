package com.mathematics.identity;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.guard.RateLimit;
import com.mathematics.guard.RequireLogin;
import com.mathematics.identity.IdentityDtos.DeleteAccountRequest;
import com.mathematics.identity.IdentityDtos.UpdateProfileRequest;
import com.mathematics.identity.IdentityDtos.UserProfile;
import com.mathematics.support.ApiException;

import jakarta.validation.Valid;

@RestController
@RequireLogin
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserRepository users;
    private final AccountDeletionService deletion;

    public UserController(UserRepository users, AccountDeletionService deletion) {
        this.users = users;
        this.deletion = deletion;
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

    /** 带密码确认，等于一个登录口：限流防止拿着偷来的令牌猜密码。 */
    @DeleteMapping
    @RateLimit(name = "delete-account", limit = 5, window = "PT10M", by = RateLimit.By.USER_OR_IP)
    public ResponseEntity<Void> delete(CurrentUser me, @Valid @RequestBody DeleteAccountRequest request) {
        deletion.delete(me.requireId(), request.password());
        return ResponseEntity.noContent().build();
    }

    private UserProfile load(long userId) {
        return users.findById(userId).map(UserProfile::of)
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
