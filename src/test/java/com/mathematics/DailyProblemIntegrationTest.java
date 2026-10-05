package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:daily-problem;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "NON_KEYWORDS=USER,YEAR,VALUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class DailyProblemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Test
    void sameProblemAllDayAndStoredOnce() throws Exception {
        long first = call(get("/api/v1/daily")).get("problem").get("id").asLong();
        long second = call(get("/api/v1/daily")).get("problem").get("id").asLong();
        assertEquals(first, second);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM daily_problem", Integer.class));
    }

    @Test
    void anonymousGetsNoPersonalFields() throws Exception {
        JsonNode body = call(get("/api/v1/daily"));
        assertFalse(body.has("streak"));
        assertFalse(body.has("solved"));
    }

    @Test
    void unpublishedPickIsReplacedTheSameDay() throws Exception {
        LocalDate today = LocalDate.now(clock);
        long picked = call(get("/api/v1/daily")).get("problem").get("id").asLong();
        jdbc.update("UPDATE problem SET status = 'DRAFT' WHERE id = ?", picked);
        try {
            long replaced = call(get("/api/v1/daily")).get("problem").get("id").asLong();
            assertNotEquals(picked, replaced);
            assertEquals(replaced, jdbc.queryForObject(
                    "SELECT problem_id FROM daily_problem WHERE for_date = ?", Long.class, java.sql.Date.valueOf(today)));
        } finally {
            jdbc.update("UPDATE problem SET status = 'PUBLISHED' WHERE id = ?", picked);
        }
    }

    @Test
    void streakAndSolvedReflectSubmissions() throws Exception {
        JsonNode issued = register();
        String token = issued.get("accessToken").asText();
        long userId = issued.get("userId").asLong();
        LocalDate today = LocalDate.now(clock);

        JsonNode before = call(get("/api/v1/daily").header("Authorization", "Bearer " + token));
        assertEquals(0, before.get("streak").asInt());
        assertFalse(before.get("practicedToday").asBoolean());
        assertFalse(before.get("solved").asBoolean());

        long dailyId = before.get("problem").get("id").asLong();
        long versionId = jdbc.queryForObject(
                "SELECT current_version_id FROM problem WHERE id = ?", Long.class, dailyId);
        insertSubmission(userId, 1, today.minusDays(1));
        insertSubmission(userId, 1, today.minusDays(2));

        JsonNode yesterdayOnly = call(get("/api/v1/daily").header("Authorization", "Bearer " + token));
        assertEquals(2, yesterdayOnly.get("streak").asInt(), "今天还没做不算断签");

        String answer = jdbc.queryForObject(
                "SELECT answer_json FROM problem_version WHERE id = ?", String.class, versionId);
        String submission = """
                {"problemId":%d,"problemVersionId":%d,"answer":%s,"durationMs":1000}
                """.formatted(dailyId, versionId, answer);
        JsonNode graded = call(post("/api/v1/submissions").header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(submission));
        assertEquals("CORRECT", graded.get("result").asText());

        JsonNode after = call(get("/api/v1/daily").header("Authorization", "Bearer " + token));
        assertEquals(3, after.get("streak").asInt());
        assertTrue(after.get("practicedToday").asBoolean());
        assertTrue(after.get("attempted").asBoolean());
        assertTrue(after.get("solved").asBoolean());
    }

    private void insertSubmission(long userId, long problemId, LocalDate on) {
        long versionId = jdbc.queryForObject(
                "SELECT current_version_id FROM problem WHERE id = ?", Long.class, problemId);
        jdbc.update("""
                INSERT INTO submission (user_id, problem_id, problem_version_id, answer_json, result,
                                        score, max_score, idempotency_key, created_at)
                VALUES (?, ?, ?, '{}', 'WRONG', 0, 1, ?, ?)
                """, userId, problemId, versionId, UUID.randomUUID().toString(), Timestamp.valueOf(on.atTime(20, 0)));
    }

    private JsonNode register() throws Exception {
        return call(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"打卡\",\"email\":\"daily-%s@example.com\",\"password\":\"demo12345\"}"
                        .formatted(UUID.randomUUID())));
    }

    private JsonNode call(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
