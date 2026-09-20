package com.mathematics.identity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthTokenRepository {

    private final JdbcTemplate jdbc;

    public AuthTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long userId, String tokenHash, String tokenType, LocalDateTime expiresAt) {
        jdbc.update("""
                INSERT INTO auth_token (user_id, token_hash, token_type, expires_at)
                VALUES (?, ?, ?, ?)
                """, userId, tokenHash, tokenType, expiresAt);
    }

    /**
     * 只认没撤销且没过期的令牌。
     */
    public Optional<Long> findActiveUserId(String tokenHash, String tokenType) {
        List<Long> ids = jdbc.query("""
                SELECT user_id FROM auth_token
                 WHERE token_hash = ? AND token_type = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                """, (rs, rowNum) -> rs.getLong("user_id"), tokenHash, tokenType);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    public int revoke(String tokenHash) {
        return jdbc.update("UPDATE auth_token SET revoked_at = CURRENT_TIMESTAMP "
                + "WHERE token_hash = ? AND revoked_at IS NULL", tokenHash);
    }

    public int revokeAllForUser(long userId) {
        return jdbc.update("UPDATE auth_token SET revoked_at = CURRENT_TIMESTAMP "
                + "WHERE user_id = ? AND revoked_at IS NULL", userId);
    }
}
