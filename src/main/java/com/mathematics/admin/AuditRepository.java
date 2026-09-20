package com.mathematics.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * audit_log 只追加，不更新也不删除。改题与重判是后面回溯问题时唯一的线索来源。
 */
@Repository
public class AuditRepository {

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(long actorId, String action, String targetType, long targetId, String detailJson) {
        jdbc.update("""
                INSERT INTO audit_log (actor_id, action, target_type, target_id, detail_json)
                VALUES (?, ?, ?, ?, ?)
                """, actorId, action, targetType, targetId, detailJson);
    }
}
