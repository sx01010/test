package com.mathematics.identity;

import java.time.LocalDateTime;

public record UserRow(
        long id,
        String nickname,
        String email,
        String phone,
        String passwordHash,
        int avatarPreset,
        String role,
        boolean practiceMode,
        String status,
        int failCount,
        LocalDateTime lockedUntil
) {
}
