package com.mathematics.admin;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mathematics.admin.AdminDtos.AdminProblemSummary;
import com.mathematics.admin.AdminDtos.SourceInput;

/**
 * 管理端写入与读回。公开读路径仍然走 {@link com.mathematics.problem.ProblemRepository}，
 * 那边所有查询都带 status = 'PUBLISHED'，草稿不会顺着公开接口漏出去。
 *
 * <p>updated_at 每次都显式赋值：MySQL 建表带了 ON UPDATE CURRENT_TIMESTAMP，H2 脚本没有，
 * 写在 SQL 里两边行为才一致。
 */
@Repository
public class AdminProblemRepository {

    /** 只把 id 声明成生成列，否则 H2 会把带默认值的 created_at 也算进生成键。 */
    private static final String[] GENERATED_ID = {"id"};

    private static final RowMapper<AdminProblemSummary> SUMMARY = (rs, rowNum) -> new AdminProblemSummary(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("problem_type"),
            rs.getInt("difficulty"),
            rs.getString("grade"),
            rs.getString("status"),
            rs.getString("origin_type"),
            rs.getObject("version_no") == null ? null : rs.getInt("version_no"),
            rs.getString("updated_at"));

    private static final RowMapper<SourceInput> SOURCE = (rs, rowNum) -> new SourceInput(
            rs.getString("origin_type"),
            rs.getString("contest_name"),
            rs.getObject("year") == null ? null : rs.getInt("year"),
            rs.getString("round"),
            rs.getString("rewrite_note"),
            rs.getString("license_ref"),
            rs.getString("source_url"));

    private static final RowMapper<Head> HEAD = (rs, rowNum) -> new Head(
            rs.getLong("id"),
            rs.getString("status"),
            rs.getObject("current_version_id") == null ? null : rs.getLong("current_version_id"));

    private static final RowMapper<DetailRow> DETAIL = (rs, rowNum) -> new DetailRow(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("problem_type"),
            rs.getInt("difficulty"),
            rs.getString("grade"),
            rs.getString("status"),
            rs.getString("stem_md"),
            rs.getString("options_json"),
            rs.getString("answer_json"),
            rs.getString("explanation_md"),
            rs.getString("grader_config_json"),
            rs.getInt("max_score"),
            rs.getInt("version_no"),
            rs.getLong("version_id"));

    private static final RowMapper<VersionPayload> PAYLOAD = (rs, rowNum) -> new VersionPayload(
            rs.getLong("id"),
            rs.getInt("version_no"),
            rs.getString("stem_md"),
            rs.getString("options_json"),
            rs.getString("answer_json"),
            rs.getString("explanation_md"),
            rs.getString("grader_config_json"),
            rs.getInt("max_score"));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public AdminProblemRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    /** 题目头，不含内容。deleted_at 非空的软删题目当作不存在。 */
    public record Head(long id, String status, Long currentVersionId) {
    }

    /** 编辑页要回填的全部字段，问题头与当前版本拼在一行里读出来。 */
    public record DetailRow(
            long id,
            String title,
            String problemType,
            int difficulty,
            String grade,
            String status,
            String stemMd,
            String optionsJson,
            String answerJson,
            String explanationMd,
            String graderConfigJson,
            int maxScore,
            int versionNo,
            long versionId) {
    }

    /** 决定要不要升版时比对的那几个字段，全部来自 problem_version。 */
    public record VersionPayload(
            long versionId,
            int versionNo,
            String stemMd,
            String optionsJson,
            String answerJson,
            String explanationMd,
            String graderConfigJson,
            int maxScore) {
    }

    public long insertProblem(String title, String type, int difficulty, String grade,
                              String status, long createdBy) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO problem (title, problem_type, difficulty, grade, status, created_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, GENERATED_ID);
            ps.setString(1, title);
            ps.setString(2, type);
            ps.setInt(3, difficulty);
            ps.setString(4, grade);
            ps.setString(5, status);
            ps.setLong(6, createdBy);
            return ps;
        }, keys);
        return requireKey(keys, "problem");
    }

    public long insertVersion(long problemId, int versionNo, String stemMd, String optionsJson,
                              String answerJson, String explanationMd, String graderConfigJson,
                              int maxScore, String changeNote, long createdBy) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO problem_version (problem_id, version_no, stem_md, options_json, answer_json,
                                                 explanation_md, grader_config_json, max_score, change_note,
                                                 created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, GENERATED_ID);
            ps.setLong(1, problemId);
            ps.setInt(2, versionNo);
            ps.setString(3, stemMd);
            ps.setString(4, optionsJson);
            ps.setString(5, answerJson);
            ps.setString(6, explanationMd);
            ps.setString(7, graderConfigJson);
            ps.setInt(8, maxScore);
            ps.setString(9, changeNote);
            ps.setLong(10, createdBy);
            return ps;
        }, keys);
        return requireKey(keys, "problem_version");
    }

    public void updateHead(long problemId, String title, String type, int difficulty, String grade) {
        jdbc.update("""
                UPDATE problem
                   SET title = ?, problem_type = ?, difficulty = ?, grade = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, title, type, difficulty, grade, problemId);
    }

    public void updateStatus(long problemId, String status) {
        jdbc.update("UPDATE problem SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                status, problemId);
    }

    public void updateCurrentVersion(long problemId, long versionId) {
        jdbc.update("UPDATE problem SET current_version_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                versionId, problemId);
    }

    public int nextVersionNo(long problemId) {
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) + 1 FROM problem_version WHERE problem_id = ?",
                Integer.class, problemId);
        return next == null ? 1 : next;
    }

    public Optional<Head> findHead(long problemId) {
        return single(jdbc.query(
                "SELECT id, status, current_version_id FROM problem WHERE id = ? AND deleted_at IS NULL",
                HEAD, problemId));
    }

    public Optional<VersionPayload> findCurrentVersion(long problemId) {
        return single(jdbc.query("""
                SELECT pv.id, pv.version_no, pv.stem_md, pv.options_json, pv.answer_json,
                       pv.explanation_md, pv.grader_config_json, pv.max_score
                  FROM problem p
                  JOIN problem_version pv ON pv.id = p.current_version_id
                 WHERE p.id = ?
                """, PAYLOAD, problemId));
    }

    public Optional<DetailRow> findDetail(long problemId) {
        return single(jdbc.query("""
                SELECT p.id, p.title, p.problem_type, p.difficulty, p.grade, p.status,
                       pv.id AS version_id, pv.version_no, pv.stem_md, pv.options_json, pv.answer_json,
                       pv.explanation_md, pv.grader_config_json, pv.max_score
                  FROM problem p
                  JOIN problem_version pv ON pv.id = p.current_version_id
                 WHERE p.id = ? AND p.deleted_at IS NULL
                """, DETAIL, problemId));
    }

    public Optional<SourceInput> findSource(long problemId) {
        return single(jdbc.query("""
                SELECT origin_type, contest_name, year, round, rewrite_note, license_ref, source_url
                  FROM problem_source WHERE problem_id = ?
                """, SOURCE, problemId));
    }

    public void replaceTags(long problemId, List<Long> tagIds) {
        jdbc.update("DELETE FROM problem_tag WHERE problem_id = ?", problemId);
        List<Object[]> batch = new ArrayList<>(tagIds.size());
        for (Long tagId : tagIds) {
            batch.add(new Object[]{problemId, tagId});
        }
        jdbc.batchUpdate("INSERT INTO problem_tag (problem_id, tag_id) VALUES (?, ?)", batch);
    }

    /**
     * problem_source 是一对一挂题目的，先 UPDATE 再按影响行数决定要不要 INSERT。
     * 不用 MySQL 的 ON DUPLICATE KEY，也不用 H2 的 MERGE，这样两套数据库走同一条语句。
     */
    public void upsertSource(long problemId, SourceInput source) {
        int updated = jdbc.update("""
                UPDATE problem_source
                   SET origin_type = ?, contest_name = ?, year = ?, round = ?,
                       rewrite_note = ?, license_ref = ?, source_url = ?
                 WHERE problem_id = ?
                """, source.originType(), source.contestName(), source.year(), source.round(),
                source.rewriteNote(), source.licenseRef(), source.sourceUrl(), problemId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO problem_source (problem_id, origin_type, contest_name, year, round,
                                                rewrite_note, license_ref, source_url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, problemId, source.originType(), source.contestName(), source.year(), source.round(),
                    source.rewriteNote(), source.licenseRef(), source.sourceUrl());
        }
    }

    public int countExistingTags(List<Long> tagIds) {
        Integer count = named.queryForObject("SELECT COUNT(1) FROM tag WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", tagIds), Integer.class);
        return count == null ? 0 : count;
    }

    public List<AdminProblemSummary> list(String status, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.title, p.problem_type, p.difficulty, p.grade, p.status, p.updated_at,
                       ps.origin_type, pv.version_no
                  FROM problem p
                  LEFT JOIN problem_version pv ON pv.id = p.current_version_id
                  LEFT JOIN problem_source ps ON ps.problem_id = p.id
                 WHERE p.deleted_at IS NULL
                """);
        List<Object> args = new ArrayList<>();
        if (status != null) {
            sql.append(" AND p.status = ? ");
            args.add(status);
        }
        sql.append(" ORDER BY p.updated_at DESC, p.id DESC LIMIT ? ");
        args.add(limit);
        return jdbc.query(sql.toString(), SUMMARY, args.toArray());
    }

    private static long requireKey(KeyHolder keys, String table) {
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("insert " + table + " returned no key");
        }
        return key.longValue();
    }

    private static <T> Optional<T> single(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
