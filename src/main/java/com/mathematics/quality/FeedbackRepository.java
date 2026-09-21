package com.mathematics.quality;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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

    public Optional<Ticket> findById(long id) {
        List<Ticket> rows = jdbc.query(SELECT + " WHERE f.id = ?", TICKET, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** 管理端队列，默认看 OPEN。先来先处理，所以按 id 升序。 */
    public List<Ticket> listByStatus(String status, int limit) {
        return jdbc.query(SELECT + " WHERE f.status = ? ORDER BY f.id ASC LIMIT ?", TICKET, status, limit);
    }

    /**
     * 结案。WHERE 里再钉一次 status = 'OPEN'：并发下两个管理员同时点结案，
     * 只有一个能改到行，另一个拿到 0 并被上层拒掉，重判也就不会跑两遍。
     */
    public int resolve(long id, String status, long handledBy, String remark) {
        return jdbc.update("""
                UPDATE problem_feedback
                   SET status = ?, handled_by = ?, handled_at = CURRENT_TIMESTAMP, remark = ?
                 WHERE id = ? AND status = 'OPEN'
                """, status, handledBy, remark, id);
    }

    private static final String SELECT = """
            SELECT f.id, f.user_id, f.problem_id, f.problem_version_id, p.title, p.current_version_id,
                   f.reason, f.detail, f.status, f.created_at
              FROM problem_feedback f
              JOIN problem p ON p.id = f.problem_id
            """;

    private static final RowMapper<Ticket> TICKET = (rs, rowNum) -> new Ticket(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getLong("problem_id"),
            rs.getLong("problem_version_id"),
            rs.getString("title"),
            rs.getObject("current_version_id") == null ? null : rs.getLong("current_version_id"),
            rs.getString("reason"),
            rs.getString("detail"),
            rs.getString("status"),
            rs.getObject("created_at", LocalDateTime.class));

    /**
     * 一条纠错工单。带上题目当前版本，结案时要用它判断「管理员是不是已经在录题界面改过了」。
     */
    public record Ticket(
            long id,
            long userId,
            long problemId,
            long problemVersionId,
            String problemTitle,
            Long currentVersionId,
            String reason,
            String detail,
            String status,
            LocalDateTime createdAt) {

        public boolean open() {
            return "OPEN".equals(status);
        }

        /** 工单指着的版本已经不是当前版本，说明题目在提单之后被改过。 */
        public boolean problemAlreadyRevised() {
            return currentVersionId != null && currentVersionId != problemVersionId;
        }
    }
}
