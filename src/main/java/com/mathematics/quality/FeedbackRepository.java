package com.mathematics.quality;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackRepository {

    /** 只把 id 声明成生成列，否则 created_at 也会被 H2 当成生成键返回。 */
    private static final String[] GENERATED_ID = {"id"};

    private final JdbcTemplate jdbc;

    public FeedbackRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findOpenId(long userId, long problemId) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM problem_feedback
                 WHERE user_id = ? AND problem_id = ? AND status = 'OPEN'
                 ORDER BY id DESC
                """, Long.class, userId, problemId);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    public long insertOpen(long userId, long problemId, long versionId, String reason, String detail) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO problem_feedback (user_id, problem_id, problem_version_id, reason, detail, status)
                    VALUES (?, ?, ?, ?, ?, 'OPEN')
                    """, GENERATED_ID);
            ps.setLong(1, userId);
            ps.setLong(2, problemId);
            ps.setLong(3, versionId);
            ps.setString(4, reason);
            ps.setString(5, detail);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("insert feedback returned no key");
        }
        return key.longValue();
    }
}
