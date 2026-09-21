package com.mathematics.practice;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mathematics.practice.PracticeDtos.TagProgress;

@Repository
public class TagProgressRepository {

    private final JdbcTemplate jdbc;

    public TagProgressRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * R14：只按知识点聚合。一道题最多挂 3 个知识点，所以一次提交最多更新 3 行。
     */
    public void record(long userId, long tagId, boolean correct, LocalDateTime at) {
        int increment = correct ? 1 : 0;
        int updated = jdbc.update("""
                UPDATE user_tag_progress
                   SET attempt_count = attempt_count + 1,
                       correct_count = correct_count + ?,
                       last_submitted_at = ?
                 WHERE user_id = ? AND tag_id = ?
                """, increment, at, userId, tagId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO user_tag_progress (user_id, tag_id, attempt_count, correct_count, last_submitted_at)
                    VALUES (?, ?, 1, ?, ?)
                    """, userId, tagId, increment, at);
        }
    }

    /**
     * 重判修正正确数，不动作答次数——学生并没有重新作答，作答次数凭什么变。
     *
     * <p>WHERE 里带边界保护：正确数不允许越过 0 与作答次数。真要越界了说明统计已经和提交记录
     * 不一致，这时候宁可这一行不动，也不要写出一个大于作答次数的正确数。
     */
    public void adjustCorrect(long userId, long tagId, int delta) {
        jdbc.update("""
                UPDATE user_tag_progress
                   SET correct_count = correct_count + ?
                 WHERE user_id = ? AND tag_id = ?
                   AND correct_count + ? >= 0
                   AND correct_count + ? <= attempt_count
                """, delta, userId, tagId, delta, delta);
    }

    /**
     * 弱项排前，正确率相同时作答多的排前面。
     */
    public List<TagProgress> listByUser(long userId) {
        return jdbc.query("""
                SELECT utp.tag_id, t.name AS tag_name, utp.attempt_count, utp.correct_count, utp.last_submitted_at
                  FROM user_tag_progress utp
                  JOIN tag t ON t.id = utp.tag_id
                 WHERE utp.user_id = ?
                 ORDER BY (utp.correct_count * 1.0 / utp.attempt_count) ASC, utp.attempt_count DESC
                """, (rs, rowNum) -> new TagProgress(
                rs.getLong("tag_id"),
                rs.getString("tag_name"),
                rs.getInt("attempt_count"),
                rs.getInt("correct_count"),
                rs.getObject("last_submitted_at", LocalDateTime.class)), userId);
    }
}
