package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 端到端跑一遍刷题闭环：注册 → 筛题 → 提交判分 → 错题本 → 掌握 → 解析闸门 → 纠错。
 * 用默认 profile（内存 H2 + Flyway 种子数据），不依赖外部 MySQL。
 *
 * <p>种子题目：1 单选答 B，2 填空答 9，3 多选答 A/C，4 判断答 true，5 多空答 16 / 32。
 * 题目 id 与版本 id 在种子里一一对应，所以下面直接用同一个数字。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PracticeFlowIntegrationTest {

    private static final long SINGLE = 1L;
    private static final long MULTI = 3L;
    private static final long JUDGE = 4L;
    private static final long BLANK = 5L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;

    @BeforeEach
    void registerFreshUser() throws Exception {
        // 每个用例一个新账号，错题本与进度互不干扰
        String email = "user-" + UUID.randomUUID() + "@example.com";
        JsonNode tokens = postJson("/api/v1/auth/register", """
                {"nickname":"小满","email":"%s","password":"demo12345"}
                """.formatted(email), null, null, status().isOk());
        accessToken = tokens.get("accessToken").asText();
    }

    @Test
    void problemListAndDetailNeverLeakAnswers() throws Exception {
        mockMvc.perform(get("/api/v1/problems").param("type", "SINGLE,MULTI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].answerJson").doesNotExist())
                .andExpect(jsonPath("$.items[0].tags[0].name").exists());

        mockMvc.perform(get("/api/v1/problems/{id}", SINGLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SINGLE"))
                .andExpect(jsonPath("$.options.length()").value(4))
                .andExpect(jsonPath("$.source.originType").value("ADAPTED"))
                .andExpect(jsonPath("$.source.rewriteNote").doesNotExist())
                .andExpect(jsonPath("$.answerJson").doesNotExist())
                .andExpect(jsonPath("$.explanationMd").doesNotExist());
    }

    @Test
    void blankProblemExposesBlankCountButNotAnswers() throws Exception {
        mockMvc.perform(get("/api/v1/problems/{id}", BLANK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("BLANK"))
                .andExpect(jsonPath("$.blankCount").value(2))
                .andExpect(jsonPath("$.answerJson").doesNotExist());
    }

    @Test
    void keywordSearchMatchesTitleAndStem() throws Exception {
        mockMvc.perform(get("/api/v1/problems").param("q", "正方形"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(BLANK));
    }

    @Test
    void wrongAnswerEntersWrongBookAndRepeatedCorrectAnswersMasterIt() throws Exception {
        assertEquals("WRONG", submit(SINGLE, "{\"choice\":\"A\"}").get("result").asText());

        mockMvc.perform(get("/api/v1/me/wrong-items").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].problemId").value(SINGLE))
                .andExpect(jsonPath("$.items[0].wrongCount").value(1))
                .andExpect(jsonPath("$.items[0].versionChanged").value(false));

        // mastery-threshold=2：连续做对两次自动移出待复习
        submit(SINGLE, "{\"choice\":\"B\"}");
        mockMvc.perform(get("/api/v1/me/wrong-items").header("Authorization", bearer()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].consecutiveCorrect").value(1));

        submit(SINGLE, "{\"choice\":\"B\"}");
        mockMvc.perform(get("/api/v1/me/wrong-items").header("Authorization", bearer()))
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/v1/me/wrong-items").param("mastered", "1").header("Authorization", bearer()))
                .andExpect(jsonPath("$.items[0].mastered").value(true));

        mockMvc.perform(get("/api/v1/me/progress").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tagName").value("行程问题"))
                .andExpect(jsonPath("$[0].attemptCount").value(3))
                .andExpect(jsonPath("$[0].correctCount").value(2));
    }

    @Test
    void manuallyMarkingMasteredMovesItemOutOfReviewQueue() throws Exception {
        submit(JUDGE, "{\"value\":false}");

        mockMvc.perform(patch("/api/v1/me/wrong-items/{problemId}", JUDGE)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mastered\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mastered").value(true));

        mockMvc.perform(get("/api/v1/me/wrong-items").header("Authorization", bearer()))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void sameIdempotencyKeyReplaysTheFirstSubmission() throws Exception {
        String key = UUID.randomUUID().toString();
        JsonNode first = submitWithKey(SINGLE, "{\"choice\":\"B\"}", key);

        // 网络重试：同一个 key 拿回同一条提交，进度不该被重复累加
        JsonNode replay = submitWithKey(SINGLE, "{\"choice\":\"A\"}", key);
        assertEquals(first.get("id").asLong(), replay.get("id").asLong());
        assertEquals("CORRECT", replay.get("result").asText());

        mockMvc.perform(get("/api/v1/me/progress").header("Authorization", bearer()))
                .andExpect(jsonPath("$[0].attemptCount").value(1));
    }

    @Test
    void submissionWithoutIdempotencyKeyIsRejected() throws Exception {
        postJson("/api/v1/submissions", """
                {"problemId":1,"problemVersionId":1,"answer":{"choice":"B"}}
                """, accessToken, null, status().isBadRequest());
    }

    @Test
    void submittingAgainstAnOldVersionIsRejected() throws Exception {
        JsonNode error = postJson("/api/v1/submissions", """
                {"problemId":1,"problemVersionId":999,"answer":{"choice":"B"}}
                """, accessToken, UUID.randomUUID().toString(), status().isConflict());
        assertEquals("PROBLEM_VERSION_STALE", error.get("code").asText());
    }

    @Test
    void malformedAnswerIsRejectedInsteadOfScoredZero() throws Exception {
        JsonNode error = postJson("/api/v1/submissions", """
                {"problemId":1,"problemVersionId":1,"answer":{"choices":["A"]}}
                """, accessToken, UUID.randomUUID().toString(), status().isBadRequest());
        assertEquals("VALIDATION_ERROR", error.get("code").asText());
    }

    @Test
    void blankProblemIsScoredPerBlank() throws Exception {
        JsonNode partial = submit(BLANK, "{\"blanks\":[\"16\",\"999\"]}");
        assertEquals("PARTIAL", partial.get("result").asText());
        assertEquals(50.0, partial.get("score").asDouble(), 0.001);

        JsonNode blanks = partial.get("details").get("blanks");
        assertTrue(blanks.get(0).get("correct").asBoolean());
        assertFalse(blanks.get(1).get("correct").asBoolean());
    }

    @Test
    void practiceModeHidesExplanationUntilFirstSubmission() throws Exception {
        // 默认随时可看，匿名也能看
        mockMvc.perform(get("/api/v1/problems/{id}/explanation", SINGLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanationMd").exists());

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"practiceMode\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.practiceMode").value(true));

        mockMvc.perform(get("/api/v1/problems/{id}/explanation", MULTI).header("Authorization", bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTENT_NOT_VISIBLE"));

        submit(MULTI, "{\"choices\":[\"A\",\"C\"]}");
        mockMvc.perform(get("/api/v1/problems/{id}/explanation", MULTI).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerJson.choices[0]").value("A"));
    }

    @Test
    void secondFeedbackOnSameProblemReusesTheOpenTicket() throws Exception {
        JsonNode first = postJson("/api/v1/problems/1/feedback",
                "{\"reason\":\"ANSWER_ERROR\",\"detail\":\"选项 B 的数值像是笔误\"}",
                accessToken, null, status().isOk());
        assertFalse(first.get("reused").asBoolean());

        JsonNode second = postJson("/api/v1/problems/1/feedback", "{\"reason\":\"TYPO\"}",
                accessToken, null, status().isOk());
        assertTrue(second.get("reused").asBoolean());
        assertEquals(first.get("feedbackId").asLong(), second.get("feedbackId").asLong());
    }

    @Test
    void submissionRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/submissions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":1,\"problemVersionId\":1,\"answer\":{\"choice\":\"B\"}}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void refreshRotatesTokensAndInvalidatesTheOldOne() throws Exception {
        String email = "rotate-" + UUID.randomUUID() + "@example.com";
        JsonNode first = postJson("/api/v1/auth/register", """
                {"nickname":"轮换","email":"%s","password":"demo12345"}
                """.formatted(email), null, null, status().isOk());

        JsonNode second = postJson("/api/v1/auth/refresh",
                "{\"refreshToken\":\"%s\"}".formatted(first.get("refreshToken").asText()),
                null, null, status().isOk());
        assertNotEquals(first.get("accessToken").asText(), second.get("accessToken").asText());

        // 旧 refresh token 立即作废
        postJson("/api/v1/auth/refresh",
                "{\"refreshToken\":\"%s\"}".formatted(first.get("refreshToken").asText()),
                null, null, status().isUnauthorized());
    }

    @Test
    void wrongPasswordLocksAccountAfterFiveAttempts() throws Exception {
        String email = "lock-" + UUID.randomUUID() + "@example.com";
        postJson("/api/v1/auth/register", """
                {"nickname":"锁定","email":"%s","password":"demo12345"}
                """.formatted(email), null, null, status().isOk());

        String loginBody = "{\"account\":\"%s\",\"password\":\"wrong-password\"}".formatted(email);
        for (int attempt = 0; attempt < 5; attempt++) {
            postJson("/api/v1/auth/login", loginBody, null, null, status().isUnauthorized());
        }

        JsonNode locked = postJson("/api/v1/auth/login",
                "{\"account\":\"%s\",\"password\":\"demo12345\"}".formatted(email),
                null, null, status().isLocked());
        assertEquals("ACCOUNT_LOCKED", locked.get("code").asText());
    }

    @Test
    void similarProblemsShareTagsAndSkipSolvedOnes() throws Exception {
        // 4 和 5 都挂「平面几何」，难度 2 与 3 相差 1，互为相似题
        JsonNode before = getJson("/api/v1/problems/" + JUDGE + "/similar", false);
        assertTrue(containsId(before, BLANK), "做题前应该推荐同知识点的多空题");

        submit(BLANK, "{\"blanks\":[\"16\",\"32\"]}");

        JsonNode after = getJson("/api/v1/problems/" + JUDGE + "/similar", true);
        assertFalse(containsId(after, BLANK), "已经做对的题不该继续出现在相似题里");
    }

    private static boolean containsId(JsonNode array, long id) {
        for (JsonNode item : array) {
            if (item.get("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }

    private JsonNode submit(long problemId, String answerJson) throws Exception {
        return submitWithKey(problemId, answerJson, UUID.randomUUID().toString());
    }

    private JsonNode submitWithKey(long problemId, String answerJson, String idempotencyKey) throws Exception {
        String body = """
                {"problemId":%d,"problemVersionId":%d,"answer":%s,"durationMs":42000}
                """.formatted(problemId, problemId, answerJson);
        return postJson("/api/v1/submissions", body, accessToken, idempotencyKey, status().isOk());
    }

    private JsonNode getJson(String path, boolean authenticated) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if (authenticated) {
            request = request.header("Authorization", bearer());
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode postJson(String path, String body, String token, String idempotencyKey,
                              ResultMatcher expected) throws Exception {
        MockHttpServletRequestBuilder request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        MvcResult result = mockMvc.perform(request).andExpect(expected).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }
}
