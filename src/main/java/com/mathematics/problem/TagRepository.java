package com.mathematics.problem;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TagRepository {

    static final RowMapper<TagRow> MAPPER = (rs, rowNum) -> new TagRow(
            rs.getLong("id"),
            rs.getObject("parent_id") == null ? null : rs.getLong("parent_id"),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getInt("sort_order"));

    private final JdbcTemplate jdbc;

    public TagRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TagRow> findAll() {
        return jdbc.query("""
                SELECT id, parent_id, name, slug, sort_order
                  FROM tag
                 ORDER BY COALESCE(parent_id, 0), sort_order, id
                """, MAPPER);
    }
}
