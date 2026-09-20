package com.mathematics.identity;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    /** 只把 id 声明成生成列，见 {@link #insert} 里的说明。 */
    private static final String[] GENERATED_ID = {"id"};

    private static final String COLUMNS = """
            id, nickname, email, phone, password_hash, avatar_preset, role,
            practice_mode, status, fail_count, locked_until
            """;

    private static final RowMapper<UserRow> MAPPER = (rs, rowNum) -> new UserRow(
            rs.getLong("id"),
            rs.getString("nickname"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("password_hash"),
            rs.getInt("avatar_preset"),
            rs.getString("role"),
            rs.getInt("practice_mode") == 1,
            rs.getString("status"),
            rs.getInt("fail_count"),
            rs.getObject("locked_until", LocalDateTime.class));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findById(long id) {
        return single(jdbc.query("SELECT " + COLUMNS + " FROM `user` WHERE id = ?", MAPPER, id));
    }

    /**
     * 登录支持邮箱或手机号，两者都是唯一索引。
     */
    public Optional<UserRow> findByAccount(String account) {
        return single(jdbc.query("SELECT " + COLUMNS + " FROM `user` WHERE email = ? OR phone = ?",
                MAPPER, account, account));
    }

    public boolean existsByEmail(String email) {
        return exists("SELECT COUNT(1) FROM `user` WHERE email = ?", email);
    }

    public boolean existsByPhone(String phone) {
        return exists("SELECT COUNT(1) FROM `user` WHERE phone = ?", phone);
    }

    public long insert(String nickname, String email, String phone, String passwordHash, String role) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            // 显式声明只取 id：H2 会把所有带默认值的列（created_at 等）都算作生成键，
            // 那样 GeneratedKeyHolder.getKey() 会因为多键而抛异常。
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO `user` (nickname, email, phone, password_hash, avatar_preset, role,
                                        practice_mode, status, fail_count)
                    VALUES (?, ?, ?, ?, 0, ?, 0, 'ACTIVE', 0)
                    """, GENERATED_ID);
            ps.setString(1, nickname);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.setString(4, passwordHash);
            ps.setString(5, role);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("insert user returned no key");
        }
        return key.longValue();
    }

    public void recordLoginFailure(long userId, int failCount, LocalDateTime lockedUntil) {
        jdbc.update("UPDATE `user` SET fail_count = ?, locked_until = ?, status = ? WHERE id = ?",
                failCount, lockedUntil, lockedUntil == null ? "ACTIVE" : "LOCKED", userId);
    }

    public void recordLoginSuccess(long userId) {
        jdbc.update("""
                UPDATE `user`
                   SET fail_count = 0, locked_until = NULL, status = 'ACTIVE', last_login_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, userId);
    }

    /**
     * 只更新传进来的字段。用动态 SQL 而不是 COALESCE(?, col)：未绑定类型的 NULL 参数在 H2 上会被拒。
     */
    public void updateProfile(long userId, String nickname, Integer avatarPreset, Boolean practiceMode) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (nickname != null) {
            sets.add("nickname = ?");
            args.add(nickname);
        }
        if (avatarPreset != null) {
            sets.add("avatar_preset = ?");
            args.add(avatarPreset);
        }
        if (practiceMode != null) {
            sets.add("practice_mode = ?");
            args.add(practiceMode ? 1 : 0);
        }
        if (sets.isEmpty()) {
            return;
        }
        sets.add("updated_at = CURRENT_TIMESTAMP");
        args.add(userId);
        jdbc.update("UPDATE `user` SET " + String.join(", ", sets) + " WHERE id = ?", args.toArray());
    }

    private boolean exists(String sql, Object arg) {
        Integer count = jdbc.queryForObject(sql, Integer.class, arg);
        return count != null && count > 0;
    }

    private Optional<UserRow> single(List<UserRow> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
