package com.mathematics.practice;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Types;
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
public class SubmissionRepository {

    /**
     * 只把 id 声明成生成列。created_at 有默认值，H2 会把它也算作生成键，
     * 那样 GeneratedKeyHolder.getKey() 会因为返回多个键而抛异常。
     */
    private static final String[] GENERATED_ID = {"id"};

    private static final String SELECT = """
            SELECT s.id, s.user_id, s.problem_id, s.problem_version_id, pv.version_no,
                   p.current_version_id, p.title, s.answer_json, s.result, s.score, s.max_score,
                   s.details_json, s.duration_ms, s.created_at
              FROM submission s
              JOIN problem_version pv ON pv.id = s.problem_version_id
              JOIN problem p ON p.id = s.problem_id
            """;

    private static final RowMapper<PracticeRows.Submission> MAPPER = (rs, rowNum) -> new PracticeRows.Submission(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getLong("problem_id"),
            rs.getLong("problem_version_id"),
            rs.getInt("version_no"),
            rs.getObject("current_version_id") == null ? null : rs.getLong("current_version_id"),
            rs.getString("title"),
            rs.getString("answer_json"),
            rs.getString("result"),
            rs.getBigDecimal("score"),
            rs.getBigDecimal("max_score"),
            rs.getString("details_json"),
            rs.getObject("duration_ms") == null ? null : rs.getInt("duration_ms"),
            rs.getObject("created_at", LocalDateTime.class));

    private final JdbcTemplate jdbc;

    public SubmissionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 唯一索引 uk_submission_idemp 是幂等的最终兜底：并发重试会在这里撞库，
     * 调用方捕获 DuplicateKeyException 后回读原记录。
     */
    public long insert(long userId, long problemId, long versionId, String answerJson, String result,
                       BigDecimal score, BigDecimal maxScore, String detailsJson, Integer durationMs,
                       String idempotencyKey) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO submission (user_id, problem_id, problem_version_id, answer_json, result,
                                            score, max_score, details_json, duration_ms, idempotency_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, GENERATED_ID);
            ps.setLong(1, userId);
            ps.setLong(2, problemId);
            ps.setLong(3, versionId);
            ps.setString(4, answerJson);
            ps.setString(5, result);
            ps.setBigDecimal(6, score);
            ps.setBigDecimal(7, maxScore);
            if (detailsJson == null) {
                ps.setNull(8, Types.VARCHAR);
            } else {
                ps.setString(8, detailsJson);
            }
            if (durationMs == null) {
                ps.setNull(9, Types.INTEGER);
            } else {
                ps.setInt(9, durationMs);
            }
            ps.setString(10, idempotencyKey);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("insert submission returned no key");
        }
        return key.longValue();
    }

    /**
     * 重判产生的新行。answer_json 照抄原提交——学生并没有重新作答，变的只是判定依据。
     * regraded_from 指向原提交，旧行原样留着：改题前判成什么样，是后面回溯问题时唯一的证据。
     */
    public long insertRegraded(long userId, long problemId, long versionId, String answerJson, String result,
                               BigDecimal score, BigDecimal maxScore, String detailsJson,
                               String idempotencyKey, long regradedFrom) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO submission (user_id, problem_id, problem_version_id, answer_json, result,
                                            score, max_score, details_json, idempotency_key, regraded_from)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, GENERATED_ID);
            ps.setLong(1, userId);
            ps.setLong(2, problemId);
            ps.setLong(3, versionId);
            ps.setString(4, answerJson);
            ps.setString(5, result);
            ps.setBigDecimal(6, score);
            ps.setBigDecimal(7, maxScore);
            if (detailsJson == null) {
                ps.setNull(8, Types.VARCHAR);
            } else {
                ps.setString(8, detailsJson);
            }
            ps.setString(9, idempotencyKey);
            ps.setLong(10, regradedFrom);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("insert regraded submission returned no key");
        }
        return key.longValue();
    }

    /**
     * 待重判的提交：这道题上、还没指向新版本、并且自己不是某次重判的前驱的行。
     *
     * <p>只取每条作答链的末端。原始提交一旦有了后继（{@code regraded_from} 指向它），
     * 末端那一行已经代表「按上一版判出来的结果」。再把原始行也重判一遍，
     * 同一次后续订正会把「错改对」算两次，正确数就虚高了。
     */
    public List<PracticeRows.Regradable> listRegradable(long problemId, long excludeVersionId) {
        return jdbc.query("""
                SELECT s.id, s.user_id, s.answer_json, s.result
                  FROM submission s
                 WHERE s.problem_id = ? AND s.problem_version_id <> ?
                   AND NOT EXISTS (SELECT 1 FROM submission child WHERE child.regraded_from = s.id)
                 ORDER BY s.id
                """, (rs, rowNum) -> new PracticeRows.Regradable(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("answer_json"),
                rs.getString("result")), problemId, excludeVersionId);
    }

    public Optional<PracticeRows.Submission> findById(long id) {
        return single(jdbc.query(SELECT + " WHERE s.id = ?", MAPPER, id));
    }

    public Optional<PracticeRows.Submission> findByIdempotencyKey(long userId, String idempotencyKey) {
        return single(jdbc.query(SELECT + " WHERE s.user_id = ? AND s.idempotency_key = ?",
                MAPPER, userId, idempotencyKey));
    }

    public List<PracticeRows.Submission> listByUser(long userId, Long problemId, Long cursor, int limitPlusOne) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE s.user_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (problemId != null) {
            sql.append(" AND s.problem_id = ? ");
            args.add(problemId);
        }
        if (cursor != null) {
            sql.append(" AND s.id < ? ");
            args.add(cursor);
        }
        sql.append(" ORDER BY s.id DESC LIMIT ? ");
        args.add(limitPlusOne);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    private Optional<PracticeRows.Submission> single(List<PracticeRows.Submission> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
