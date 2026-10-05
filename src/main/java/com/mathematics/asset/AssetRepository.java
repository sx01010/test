package com.mathematics.asset;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AssetRepository {

    public record Asset(String objectKey, String mimeType, int sizeBytes) {
    }

    private final JdbcTemplate jdbc;

    public AssetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String objectKey, String mimeType, int sizeBytes, String sha256, long createdBy) {
        jdbc.update("""
                INSERT INTO problem_asset (object_key, mime_type, size_bytes, sha256, created_by)
                VALUES (?, ?, ?, ?, ?)
                """, objectKey, mimeType, sizeBytes, sha256, createdBy);
    }

    public Optional<Asset> findByKey(String objectKey) {
        return first(jdbc.query("SELECT object_key, mime_type, size_bytes FROM problem_asset WHERE object_key = ?",
                (rs, n) -> new Asset(rs.getString(1), rs.getString(2), rs.getInt(3)), objectKey));
    }

    /** 同一张图重复上传时复用已有的对象键，免得题库里散落一堆内容相同的文件。 */
    public Optional<Asset> findBySha256(String sha256, String mimeType) {
        return first(jdbc.query("""
                SELECT object_key, mime_type, size_bytes FROM problem_asset
                 WHERE sha256 = ? AND mime_type = ? ORDER BY id LIMIT 1
                """, (rs, n) -> new Asset(rs.getString(1), rs.getString(2), rs.getInt(3)), sha256, mimeType));
    }

    private static Optional<Asset> first(List<Asset> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
