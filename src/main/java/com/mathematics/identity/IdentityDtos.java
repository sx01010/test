package com.mathematics.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class IdentityDtos {

    private IdentityDtos() {
    }

    /**
     * R01：昵称 + 邮箱或手机号 + 密码即可注册，不收集真实姓名、学校、年龄。
     */
    public record RegisterRequest(
            @NotBlank(message = "昵称不能为空") @Size(max = 32, message = "昵称最长 32 字") String nickname,
            @Email(message = "邮箱格式不正确") @Size(max = 128) String email,
            @Size(max = 20) String phone,
            @NotBlank @Size(min = 8, max = 72, message = "密码至少 8 位") String password) {
    }

    public record LoginRequest(@NotBlank String account, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(long userId, String accessToken, String refreshToken) {
    }

    public record UserProfile(long id, String nickname, int avatarPreset, String role, boolean practiceMode) {

        public static UserProfile of(UserRow row) {
            return new UserProfile(row.id(), row.nickname(), row.avatarPreset(), row.role(), row.practiceMode());
        }
    }

    public record UpdateProfileRequest(
            @Size(max = 32) String nickname,
            @Min(0) Integer avatarPreset,
            Boolean practiceMode) {
    }

    public record OkResponse(boolean ok) {
    }
}
