package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:admin-dashboard;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "NON_KEYWORDS=USER,YEAR,VALUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AdminDashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private LocalDate today;
    private long versionId;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        versionId = jdbc.queryForObject("SELECT current_version_id FROM problem WHERE id = 1", Long.class);
    }

    /**
     * 8 天前注册的两人：A 次日和第 7 天都回来了，B 再没来过 → 该批 D1 = D7 = 50%。
     * 今天注册的 C 交了一对一错；给 B 补的一条重判记录不能把 B 算成今天活跃。
     */
    @Test
    void activeUsersAndRetentionAreComputedFromSubmissions() throws Exception {
        LocalDate cohortDay = today.minusDays(8);
        long a = user(cohortDay);
        long b = user(cohortDay);
        long c = user(today);
        submit(a, cohortDay, "CORRECT", null);
        submit(a, cohortDay.plusDays(1), "CORRECT", null);
        submit(a, cohortDay.plusDays(7), "WRONG", null);
        long original = submit(b, cohortDay, "WRONG", null);
        submit(b, today, "CORRECT", original);
        submit(c, today, "CORRECT", null);
        submit(c, today, "WRONG", null);

        JsonNode body = dashboard(adminToken(), 14);

        JsonNode cohort = find(body.get("cohorts"), cohortDay);
        assertEquals(2, cohort.get("size").asInt());
        assertEquals(0.5, cohort.get("d1").asDouble());
        assertEquals(0.5, cohort.get("d7").asDouble());
        assertFalse(find(body.get("cohorts"), today).hasNonNull("d1"), "今天注册的人还没到次日，D1 应为空而不是 0");

        JsonNode day = find(body.get("days"), today);
        assertEquals(1, day.get("activeUsers").asInt(), "重判记录不算用户行为");
        assertEquals(2, day.get("submissions").asInt());
        assertEquals(0.5, day.get("correctRate").asDouble());
        assertEquals(2, find(body.get("days"), cohortDay).get("activeUsers").asInt());
        assertEquals(14, body.get("days").size());
    }

    @Test
    void dashboardIsAdminOnly() throws Exception {
        String student = register();
        mockMvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
    }

    @Test
    void rangeIsClamped() throws Exception {
        assertEquals(60, dashboard(adminToken(), 10_000).get("days").size());
    }

    private JsonNode find(JsonNode rows, LocalDate date) {
        for (JsonNode row : rows) {
            if (row.get("date").asText().equals(date.toString())) {
                return row;
            }
        }
        throw new AssertionError("没有 " + date + " 这一行");
    }

    private long user(LocalDate registeredOn) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO `user` (nickname, email, password_hash, role, status, created_at)
                    VALUES ('看板', ?, 'x', 'USER', 'ACTIVE', ?)
                    """, new String[] {"id"});
            statement.setString(1, "dash-" + UUID.randomUUID() + "@example.com");
            statement.setTimestamp(2, Timestamp.valueOf(registeredOn.atTime(9, 0)));
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private long submit(long userId, LocalDate on, String result, Long regradedFrom) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        LocalDateTime at = on.atTime(10, 0);
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO submission (user_id, problem_id, problem_version_id, answer_json, result,
                                            score, max_score, idempotency_key, regraded_from, created_at)
                    VALUES (?, 1, ?, '{}', ?, 0, 1, ?, ?, ?)
                    """, new String[] {"id"});
            statement.setLong(1, userId);
            statement.setLong(2, versionId);
            statement.setString(3, result);
            statement.setString(4, UUID.randomUUID().toString());
            statement.setObject(5, regradedFrom);
            statement.setTimestamp(6, Timestamp.valueOf(at));
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private JsonNode dashboard(String token, int days) throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/dashboard").param("days", String.valueOf(days))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String adminToken() throws Exception {
        JsonNode issued = registerRaw();
        jdbc.update("UPDATE `user` SET role = 'ADMIN' WHERE id = ?", issued.get("userId").asLong());
        return issued.get("accessToken").asText();
    }

    private String register() throws Exception {
        return registerRaw().get("accessToken").asText();
    }

    private JsonNode registerRaw() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"运营\",\"email\":\"ops-%s@example.com\",\"password\":\"demo12345\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
