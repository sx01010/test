package com.mathematics.material;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MaterialRepository {

    /** 只把 id 声明成生成列，否则 created_at 也会被 H2 当成生成键返回。 */
    private static final String[] GENERATED_ID = {"id"};

    private static final String SELECT_SUMMARY = """
            SELECT m.id, m.title, m.material_type, m.grade, m.year, m.origin_type,
                   f.original_name, f.size_bytes
              FROM material m
              JOIN material_file f ON f.material_id = m.id AND f.deleted_at IS NULL
            """;

    private static final RowMapper<MaterialRows.Summary> SUMMARY = (rs, rowNum) -> new MaterialRows.Summary(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("material_type"),
            rs.getString("grade"),
            rs.getObject("year") == null ? null : rs.getInt("year"),
            rs.getString("origin_type"),
            rs.getString("original_name"),
            rs.getLong("size_bytes"));

    private final JdbcTemplate jdbc;

    public MaterialRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** R21：只返回 PUBLISHED，按 id 倒序游标翻页。 */
    public List<MaterialRows.Summary> listPublished(String type, String grade, Integer year,
                                                   Long cursor, int limitPlusOne) {
        StringBuilder sql = new StringBuilder(SELECT_SUMMARY).append(" WHERE m.status = 'PUBLISHED' ");
        List<Object> args = new ArrayList<>();
        if (type != null) {
            sql.append(" AND m.material_type = ? ");
            args.add(type);
        }
        if (grade != null) {
            sql.append(" AND m.grade = ? ");
            args.add(grade);
        }
        if (year != null) {
            sql.append(" AND m.year = ? ");
            args.add(year);
        }
        if (cursor != null) {
            sql.append(" AND m.id < ? ");
            args.add(cursor);
        }
        sql.append(" ORDER BY m.id DESC LIMIT ? ");
        args.add(limitPlusOne);
        return jdbc.query(sql.toString(), SUMMARY, args.toArray());
    }

    public Optional<MaterialRows.Downloadable> findDownloadable(long materialId) {
        List<MaterialRows.Downloadable> rows = jdbc.query("""
                SELECT m.id, m.status, f.object_key, f.original_name, f.deleted_at
                  FROM material m
                  JOIN material_file f ON f.material_id = m.id
                 WHERE m.id = ?
                 ORDER BY f.id DESC
                """, (rs, rowNum) -> new MaterialRows.Downloadable(
                rs.getLong("id"),
                rs.getString("object_key"),
                rs.getString("original_name"),
                rs.getString("status"),
                rs.getObject("deleted_at") != null), materialId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<MaterialRows.Downloadable> findByObjectKey(String objectKey) {
        List<MaterialRows.Downloadable> rows = jdbc.query("""
                SELECT m.id, m.status, f.object_key, f.original_name, f.deleted_at
                  FROM material_file f
                  JOIN material m ON m.id = f.material_id
                 WHERE f.object_key = ?
                """, (rs, rowNum) -> new MaterialRows.Downloadable(
                rs.getLong("id"),
                rs.getString("object_key"),
                rs.getString("original_name"),
                rs.getString("status"),
                rs.getObject("deleted_at") != null), objectKey);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public long insertMaterial(String title, String materialType, String grade, Integer year,
                               String originType, String licenseRef, String sourceUrl,
                               String status, long createdBy) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO material (title, material_type, grade, year, origin_type,
                                          license_ref, source_url, status, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, GENERATED_ID);
            ps.setString(1, title);
            ps.setString(2, materialType);
            setNullable(ps, 3, grade);
            if (year == null) {
                ps.setNull(4, Types.SMALLINT);
            } else {
                ps.setInt(4, year);
            }
            ps.setString(5, originType);
            setNullable(ps, 6, licenseRef);
            setNullable(ps, 7, sourceUrl);
            ps.setString(8, status);
            ps.setLong(9, createdBy);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("insert material returned no key");
        }
        return key.longValue();
    }

    public void insertFile(long materialId, String objectKey, String originalName, long sizeBytes,
                           String sha256, String scanStatus) {
        jdbc.update("""
                INSERT INTO material_file (material_id, object_key, original_name, mime_type,
                                           size_bytes, sha256, scan_status)
                VALUES (?, ?, ?, 'application/pdf', ?, ?, ?)
                """, materialId, objectKey, originalName, sizeBytes, sha256, scanStatus);
    }

    public int updateStatus(long materialId, String status) {
        return jdbc.update(
                "UPDATE material SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, materialId);
    }

    /** R23：下架只打标记，真正删字节要一个定时任务，V1 留给运维。 */
    public void markFilesDeleted(long materialId) {
        jdbc.update("UPDATE material_file SET deleted_at = CURRENT_TIMESTAMP "
                + "WHERE material_id = ? AND deleted_at IS NULL", materialId);
    }

    private static void setNullable(PreparedStatement ps, int index, String value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}
