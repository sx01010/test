package com.mathematics.problem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mathematics.problem.ProblemDtos.SearchQuery;

@Repository
public class ProblemRepository {

    private static final String PUBLISHED = " p.status = 'PUBLISHED' AND p.deleted_at IS NULL ";

    private static final RowMapper<ProblemRows.Summary> SUMMARY = (rs, rowNum) -> new ProblemRows.Summary(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("problem_type"),
            rs.getInt("difficulty"),
            rs.getString("grade"),
            rs.getObject("current_version_id") == null ? null : rs.getLong("current_version_id"),
            rs.getString("origin_type"));

    private static final RowMapper<ProblemRows.Content> CONTENT = (rs, rowNum) -> new ProblemRows.Content(
            rs.getLong("problem_id"),
            rs.getString("title"),
            rs.getString("problem_type"),
            rs.getInt("difficulty"),
            rs.getString("grade"),
            rs.getLong("version_id"),
            rs.getInt("version_no"),
            rs.getString("stem_md"),
            rs.getString("options_json"),
            rs.getString("answer_json"),
            rs.getString("explanation_md"),
            rs.getString("grader_config_json"),
            rs.getInt("max_score"));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public ProblemRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    /**
     * R04：题型多选叠加知识点、难度、年级、来源与关键词，按 id 倒序游标翻页。
     * 关键词这里用 LIKE，跨 H2 与 MySQL 都能跑；MySQL 上的 ngram 全文索引留给量大之后再切 MATCH ... AGAINST。
     */
    public List<ProblemRows.Summary> search(SearchQuery query, Long cursor, int limitPlusOne) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.title, p.problem_type, p.difficulty, p.grade, p.current_version_id, ps.origin_type
                  FROM problem p
                  JOIN problem_version pv ON pv.id = p.current_version_id
                  LEFT JOIN problem_source ps ON ps.problem_id = p.id
                 WHERE
                """);
        sql.append(PUBLISHED);
        List<Object> args = new ArrayList<>();

        if (query.types() != null && !query.types().isEmpty()) {
            sql.append(" AND p.problem_type IN (")
                    .append(String.join(",", query.types().stream().map(t -> "?").toList()))
                    .append(") ");
            args.addAll(query.types());
        }
        if (query.difficulty() != null) {
            sql.append(" AND p.difficulty = ? ");
            args.add(query.difficulty());
        }
        if (query.grade() != null) {
            sql.append(" AND p.grade = ? ");
            args.add(query.grade());
        }
        if (query.originType() != null) {
            sql.append(" AND ps.origin_type = ? ");
            args.add(query.originType());
        }
        if (query.tagId() != null) {
            // 知识点树只有两级：选一级节点时连同它的子节点一起筛
            sql.append(" AND EXISTS (SELECT 1 FROM problem_tag pt JOIN tag t ON t.id = pt.tag_id"
                    + " WHERE pt.problem_id = p.id AND (t.id = ? OR t.parent_id = ?)) ");
            args.add(query.tagId());
            args.add(query.tagId());
        }
        if (query.keyword() != null) {
            sql.append(" AND (p.title LIKE ? OR pv.stem_md LIKE ?) ");
            String like = "%" + query.keyword() + "%";
            args.add(like);
            args.add(like);
        }
        if (cursor != null) {
            sql.append(" AND p.id < ? ");
            args.add(cursor);
        }
        sql.append(" ORDER BY p.id DESC LIMIT ? ");
        args.add(limitPlusOne);

        return jdbc.query(sql.toString(), SUMMARY, args.toArray());
    }

    public Map<Long, List<TagRow>> tagsByProblemId(List<Long> problemIds) {
        if (problemIds.isEmpty()) {
            return Map.of();
        }
        List<ProblemRows.ProblemTag> rows = named.query("""
                SELECT pt.problem_id, t.id, t.parent_id, t.name, t.slug, t.sort_order
                  FROM problem_tag pt
                  JOIN tag t ON t.id = pt.tag_id
                 WHERE pt.problem_id IN (:ids)
                 ORDER BY t.sort_order, t.id
                """, new MapSqlParameterSource("ids", problemIds),
                (rs, rowNum) -> new ProblemRows.ProblemTag(rs.getLong("problem_id"),
                        TagRepository.MAPPER.mapRow(rs, rowNum)));

        Map<Long, List<TagRow>> result = new LinkedHashMap<>();
        for (ProblemRows.ProblemTag row : rows) {
            result.computeIfAbsent(row.problemId(), key -> new ArrayList<>()).add(row.tag());
        }
        return result;
    }

    public Optional<ProblemRows.Content> findPublishedContent(long problemId) {
        List<ProblemRows.Content> rows = jdbc.query("""
                SELECT p.id AS problem_id, p.title, p.problem_type, p.difficulty, p.grade,
                       pv.id AS version_id, pv.version_no, pv.stem_md, pv.options_json,
                       pv.answer_json, pv.explanation_md, pv.grader_config_json, pv.max_score
                  FROM problem p
                  JOIN problem_version pv ON pv.id = p.current_version_id
                 WHERE p.id = ? AND
                """ + PUBLISHED, CONTENT, problemId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<ProblemRows.Source> findSource(long problemId) {
        List<ProblemRows.Source> rows = jdbc.query("""
                SELECT origin_type, contest_name, year, round FROM problem_source WHERE problem_id = ?
                """, (rs, rowNum) -> new ProblemRows.Source(
                rs.getString("origin_type"),
                rs.getString("contest_name"),
                rs.getObject("year") == null ? null : rs.getInt("year"),
                rs.getString("round")), problemId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<Long> tagIdsOf(long problemId) {
        return jdbc.queryForList("SELECT tag_id FROM problem_tag WHERE problem_id = ?", Long.class, problemId);
    }

    /**
     * R07：同知识点、难度 ±1，排除当前题；登录用户再排除已经做对过的题。
     * excludeSolvedBy 为 null 表示匿名访问。
     */
    public List<ProblemRows.Similar> findSimilar(long problemId, List<Long> tagIds, int difficulty,
                                                 Long excludeSolvedBy, boolean limitDifficulty, int limit) {
        if (tagIds.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.title, p.difficulty, COUNT(pt.tag_id) AS shared_tag_count
                  FROM problem p
                  JOIN problem_tag pt ON pt.problem_id = p.id
                 WHERE pt.tag_id IN (:tagIds) AND p.id <> :problemId AND
                """);
        sql.append(PUBLISHED);
        Map<String, Object> params = new HashMap<>();
        params.put("tagIds", tagIds);
        params.put("problemId", problemId);
        params.put("limit", limit);

        if (limitDifficulty) {
            sql.append(" AND p.difficulty BETWEEN :minDifficulty AND :maxDifficulty ");
            params.put("minDifficulty", difficulty - 1);
            params.put("maxDifficulty", difficulty + 1);
        }
        if (excludeSolvedBy != null) {
            sql.append("""
                     AND NOT EXISTS (SELECT 1 FROM submission s
                                      WHERE s.user_id = :userId AND s.problem_id = p.id AND s.result = 'CORRECT')
                    """);
            params.put("userId", excludeSolvedBy);
        }
        sql.append("""
                 GROUP BY p.id, p.title, p.difficulty
                 ORDER BY shared_tag_count DESC, p.id DESC
                 LIMIT :limit
                """);

        return named.query(sql.toString(), params, (rs, rowNum) -> new ProblemRows.Similar(
                rs.getLong("id"), rs.getString("title"), rs.getInt("difficulty"), rs.getInt("shared_tag_count")));
    }

    /**
     * R06 的练习模式闸门要判断「这道题有没有提交过」。读 submission 是跨模块的一次纯查询，
     * 放在这里可以避免 problem 与 practice 两个包互相依赖。
     */
    public boolean hasSubmission(long userId, long problemId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM submission WHERE user_id = ? AND problem_id = ?",
                Integer.class, userId, problemId);
        return count != null && count > 0;
    }
}
