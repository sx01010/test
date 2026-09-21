package com.mathematics.practice;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WrongItemRepository {

    private static final RowMapper<PracticeRows.WrongItem> MAPPER = (rs, rowNum) -> new PracticeRows.WrongItem(
            rs.getLong("id"),
            rs.getLong("problem_id"),
            rs.getString("title"),
            rs.getInt("mastered") == 1,
            rs.getInt("wrong_count"),
            rs.getInt("consecutive_correct"),
            rs.getObject("last_wrong_at", LocalDateTime.class),
            rs.getLong("last_version_id"),
            rs.getObject("current_version_id") == null ? null : rs.getLong("current_version_id"));

    private final JdbcTemplate jdbc;

    public WrongItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * R12：WRONG 和 PARTIAL 都入本。重新做错会重置连对计数并取消已掌握。
     */
    public void recordWrong(long userId, long problemId, long submissionId, long versionId, LocalDateTime at) {
        int updated = jdbc.update("""
                UPDATE wrong_item
                   SET last_submission_id = ?, last_version_id = ?, wrong_count = wrong_count + 1,
                       consecutive_correct = 0, mastered = 0, last_wrong_at = ?
                 WHERE user_id = ? AND problem_id = ?
                """, submissionId, versionId, at, userId, problemId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO wrong_item (user_id, problem_id, last_submission_id, last_version_id,
                                            wrong_count, consecutive_correct, mastered, last_wrong_at)
                    VALUES (?, ?, ?, ?, 1, 0, 0, ?)
                    """, userId, problemId, submissionId, versionId, at);
        }
    }

    /**
     * 做对只影响已经在错题本里的题：连对达到阈值就自动标记掌握。
     */
    public void recordCorrect(long userId, long problemId, long submissionId, int masteryThreshold) {
        jdbc.update("""
                UPDATE wrong_item
                   SET consecutive_correct = consecutive_correct + 1,
                       last_submission_id = ?,
                       mastered = CASE WHEN consecutive_correct + 1 >= ? THEN 1 ELSE mastered END
                 WHERE user_id = ? AND problem_id = ? AND mastered = 0
                """, submissionId, masteryThreshold, userId, problemId);
    }

    /**
     * 重判把答错翻成答对时，撤掉当初那次错误计数。
     *
     * <p>和 {@link #recordCorrect} 不是一回事：那个是学生重新做对了，连对计数该加；这里是判错本身
     * 就是我们自己的 bug，那条记录本来不该存在。所以减错误计数，减到 0 就把整行删掉——
     * 一道从来没真正做错过的题，不该赖在错题本里。
     */
    public void undoWrong(long userId, long problemId, long submissionId, long versionId) {
        jdbc.update("""
                UPDATE wrong_item
                   SET wrong_count = wrong_count - 1, last_submission_id = ?, last_version_id = ?
                 WHERE user_id = ? AND problem_id = ? AND wrong_count > 0
                """, submissionId, versionId, userId, problemId);
        jdbc.update("DELETE FROM wrong_item WHERE user_id = ? AND problem_id = ? AND wrong_count <= 0",
                userId, problemId);
    }

    public int setMastered(long userId, long problemId, boolean mastered) {
        return jdbc.update("""
                UPDATE wrong_item
                   SET mastered = ?, consecutive_correct = CASE WHEN ? = 1 THEN consecutive_correct ELSE 0 END
                 WHERE user_id = ? AND problem_id = ?
                """, mastered ? 1 : 0, mastered ? 1 : 0, userId, problemId);
    }

    /**
     * R13：按知识点与加入时间筛选，默认只看未掌握。
     */
    public List<PracticeRows.WrongItem> list(long userId, Integer mastered, Long tagId,
                                            LocalDateTime since, Long cursor, int limitPlusOne) {
        StringBuilder sql = new StringBuilder("""
                SELECT w.id, w.problem_id, p.title, w.mastered, w.wrong_count, w.consecutive_correct,
                       w.last_wrong_at, w.last_version_id, p.current_version_id
                  FROM wrong_item w
                  JOIN problem p ON p.id = w.problem_id
                 WHERE w.user_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (mastered != null) {
            sql.append(" AND w.mastered = ? ");
            args.add(mastered);
        }
        if (tagId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM problem_tag pt "
                    + "WHERE pt.problem_id = w.problem_id AND pt.tag_id = ?) ");
            args.add(tagId);
        }
        if (since != null) {
            sql.append(" AND w.last_wrong_at >= ? ");
            args.add(since);
        }
        if (cursor != null) {
            sql.append(" AND w.id < ? ");
            args.add(cursor);
        }
        sql.append(" ORDER BY w.id DESC LIMIT ? ");
        args.add(limitPlusOne);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }
}
