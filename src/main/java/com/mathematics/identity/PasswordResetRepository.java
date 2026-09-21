package com.mathematics.identity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PasswordResetRepository {

    private static final RowMapper<Code> MAPPER = (rs, rowNum) -> new Code(
            rs.getLong("id"),
            rs.getString("code_hash"),
            rs.getObject("expires_at", LocalDateTime.class),
            rs.getInt("attempt_count"));

    private final JdbcTemplate jdbc;

    public PasswordResetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 冷却判断看的是「有没有在这个时间点之后签发过」，而不是最近一条的时间差——
     * 用掉或作废的码也算发过，否则用完一个码马上就能再要一个。
     */
    public boolean issuedSince(long userId, LocalDateTime since) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM password_reset_code WHERE user_id = ? AND created_at > ?
                """, Integer.class, userId, since);
        return count != null && count > 0;
    }

    /**
     * 作废该用户所有还没用掉的码。签发新码前必须先调：否则用户连点三次就有三个码同时有效，
     * 暴破面积直接乘三。用 consumed_at 标记而不是删行，留下审计痕迹。
     */
    public int invalidateOutstanding(long userId) {
        return jdbc.update("""
                UPDATE password_reset_code SET consumed_at = CURRENT_TIMESTAMP
                 WHERE user_id = ? AND consumed_at IS NULL
                """, userId);
    }

    public void insert(long userId, String channel, String codeHash, LocalDateTime expiresAt) {
        jdbc.update("""
                INSERT INTO password_reset_code (user_id, channel, code_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, userId, channel, codeHash, expiresAt);
    }

    /**
     * 当前唯一有效的码。过期与超次数的行也要取出来，这样校验失败时能走同一条错误分支，
     * 不让调用方从「找不到」和「不匹配」的区别里推断出码的状态。
     */
    public Optional<Code> findOutstanding(long userId) {
        List<Code> rows = jdbc.query("""
                SELECT id, code_hash, expires_at, attempt_count FROM password_reset_code
                 WHERE user_id = ? AND consumed_at IS NULL
                 ORDER BY id DESC
                """, MAPPER, userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void recordFailedAttempt(long codeId) {
        jdbc.update("UPDATE password_reset_code SET attempt_count = attempt_count + 1 WHERE id = ?", codeId);
    }

    public void consume(long codeId) {
        jdbc.update("UPDATE password_reset_code SET consumed_at = CURRENT_TIMESTAMP WHERE id = ?", codeId);
    }

    public record Code(long id, String codeHash, LocalDateTime expiresAt, int attemptCount) {
    }
}
