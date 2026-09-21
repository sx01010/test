package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * R18：纠错结案与回溯重判。
 *
 * <p>场景固定成「标准答案录错了」：题目有 A/B/C 三个选项，发布时答案写成 B，实际应为 A。
 * 一个学生答 A 被判错，另一个学生答 C 本来就错。订正答案后重判，前者应该翻成答对，
 * 后者结果没变就该被跳过——一次覆盖住「该改的改了」和「不该动的没动」两件事。
 *
 * <p>独立 H2 库名：本类会发新题并改统计，混在默认库里会打挂其它测试的精确条数断言。
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:feedback-resolution;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "NON_KEYWORDS=USER,YEAR,VALUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class FeedbackResolutionIntegrationTest {

    private static final long TAG_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private long adminId;
    private String studentToken;
    private long studentId;

    @BeforeEach
    void prepareAccounts() throws Exception {
        adminToken = registerToken("纠错处理员");
        adminId = userIdOf(adminToken);
        jdbc.update("UPDATE `user` SET role = 'ADMIN' WHERE id = ?", adminId);
        studentToken = registerToken("学生甲");
        studentId = userIdOf(studentToken);
    }

    @Test
    void feedbackQueueAndResolveAreAdminOnly() throws Exception {
        long problemId = publishProblem("B");
        long feedbackId = fileFeedback(studentToken, problemId);

        mockMvc.perform(get("/api/v1/admin/feedback")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/feedback").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        String body = """
                {"decision":"REJECTED","remark":"题面没问题"}
                """;
        perform(post("/api/v1/admin/feedback/{id}/resolve", feedbackId), body, null, status().isUnauthorized());
        JsonNode forbidden = perform(post("/api/v1/admin/feedback/{id}/resolve", feedbackId),
                body, studentToken, status().isForbidden());
        assertEquals("FORBIDDEN", forbidden.get("code").asText());
    }

    @Test
    void openQueueListsTicketWithReasonAndDetail() throws Exception {
        long problemId = publishProblem("B");
        long feedbackId = fileFeedback(studentToken, problemId);

        JsonNode open = adminQueue("OPEN");
        JsonNode ticket = findById(open, feedbackId);
        assertEquals("OPEN", ticket.get("status").asText());
        assertEquals("ANSWER_ERROR", ticket.get("reason").asText());
        assertEquals(problemId, ticket.get("problemId").asLong());
        assertEquals("标准答案应该是 A", ticket.get("detail").asText());
        assertTrue(ticket.hasNonNull("problemTitle"), "队列要能直接看到题目标题，否则得再点一次才知道是哪道题");

        // 结案后就不该再出现在 OPEN 队列里
        rejectFeedback(feedbackId, "已核对");
        assertNull(findByIdOrNull(adminQueue("OPEN"), feedbackId), "已结案的工单不该留在 OPEN 队列");
        assertTrue(findByIdOrNull(adminQueue("REJECTED"), feedbackId) != null);
    }

    @Test
    void rejectOnlyChangesStatusAndLeavesProblemAlone() throws Exception {
        long problemId = publishProblem("B");
        long versionBefore = currentVersionId(problemId);
        long feedbackId = fileFeedback(studentToken, problemId);

        JsonNode resolved = rejectFeedback(feedbackId, "答案没错，是学生理解偏了");
        assertEquals("REJECTED", resolved.get("status").asText());
        assertEquals(0, resolved.get("regradedCount").asInt());

        assertEquals(versionBefore, currentVersionId(problemId), "驳回不该动题目版本");
        assertEquals(1, versionCount(problemId));
        assertEquals(adminId, handledBy(feedbackId));
        assertEquals(1, auditCount(feedbackId, "FEEDBACK_REJECT"));
    }

    @Test
    void resolvedTicketCannotBeResolvedTwice() throws Exception {
        long problemId = publishProblem("B");
        long feedbackId = fileFeedback(studentToken, problemId);
        rejectFeedback(feedbackId, "第一次结案");

        JsonNode again = perform(post("/api/v1/admin/feedback/{id}/resolve", feedbackId),
                """
                {"decision":"FIXED","regrade":true}
                """, adminToken, status().isBadRequest());
        assertEquals("VALIDATION_ERROR", again.get("code").asText());
    }

    @Test
    void fixedWithoutAnyCorrectionIsRejected() throws Exception {
        long problemId = publishProblem("B");
        long feedbackId = fileFeedback(studentToken, problemId);

        // 既没带新版本，题目当前版本也没被改过——「已修正」无从成立
        JsonNode rejected = perform(post("/api/v1/admin/feedback/{id}/resolve", feedbackId),
                """
                {"decision":"FIXED","regrade":true,"remark":"改好了"}
                """, adminToken, status().isBadRequest());
        assertEquals("VALIDATION_ERROR", rejected.get("code").asText());
        assertEquals("OPEN", statusOf(feedbackId), "被拒的结案不该改掉工单状态");
        assertEquals(0, regradedCount(problemId));
    }

    @Test
    void fixedWithNewVersionRegradesOnlyTheSubmissionsWhoseResultChanges() throws Exception {
        long problemId = publishProblem("B");
        long oldVersionId = currentVersionId(problemId);

        // 学生甲答 A：按录错的答案 B 被判错，订正后应该翻成答对
        long wrongSubmissionId = submit(studentToken, problemId, oldVersionId, "A", "WRONG");
        // 学生乙答 C：订正前后都错，重判该跳过它
        String otherToken = registerToken("学生乙");
        long otherStudentId = userIdOf(otherToken);
        long unaffectedSubmissionId = submit(otherToken, problemId, oldVersionId, "C", "WRONG");

        assertEquals(1, wrongItemCount(studentId, problemId), "答错要先进错题本");
        assertEquals(Map.of("attempt", 1, "correct", 0), progressOf(studentId));

        long feedbackId = fileFeedback(studentToken, problemId);
        JsonNode resolved = perform(post("/api/v1/admin/feedback/{id}/resolve", feedbackId),
                fixedBody("A", "标准答案录错，B 订正为 A", true), adminToken, status().isOk());

        assertEquals("FIXED", resolved.get("status").asText());
        assertEquals(1, resolved.get("regradedCount").asInt(), "只有结果变化的那一条该被重判");

        long newVersionId = currentVersionId(problemId);
        assertTrue(newVersionId != oldVersionId, "订正答案要升版");
        assertEquals(2, versionCount(problemId));

        // 新提交追加在旧行之后，旧行原样保留
        Map<String, Object> regraded = regradedRow(wrongSubmissionId);
        assertEquals("CORRECT", regraded.get("result"));
        assertEquals(newVersionId, ((Number) regraded.get("problem_version_id")).longValue());
        assertEquals("WRONG", resultOf(wrongSubmissionId), "旧提交要保留改题前的判定");
        assertNull(regradedRowOrNull(unaffectedSubmissionId), "结果没变的提交不该生出新行");
        assertEquals(1, wrongItemCount(otherStudentId, problemId), "答 C 的学生前后都错，错题本里那条本来就成立，不该被清掉");

        // 统计跟着修正：作答次数不变，正确数加一，错题本里那条本不该存在的记录被清掉
        assertEquals(Map.of("attempt", 1, "correct", 1), progressOf(studentId));
        assertEquals(0, wrongItemCount(studentId, problemId));

        assertEquals(1, auditCount(feedbackId, "FEEDBACK_FIX"));
    }

    @Test
    void laterRevisionDoesNotCountAnAlreadyRegradedSubmissionTwice() throws Exception {
        long problemId = publishProblem("B");
        long oldVersionId = currentVersionId(problemId);
        submit(studentToken, problemId, oldVersionId, "A", "WRONG");

        long firstFeedback = fileFeedback(studentToken, problemId);
        JsonNode first = perform(post("/api/v1/admin/feedback/{id}/resolve", firstFeedback),
                fixedBody("A", "标准答案录错，B 订正为 A", true), adminToken, status().isOk());
        assertEquals(1, first.get("regradedCount").asInt());
        assertEquals(Map.of("attempt", 1, "correct", 1), progressOf(studentId));

        // 答案已经对了，这次只改题干。学生的答案按新版本仍然是对的，正确数不该再加一
        long secondFeedback = fileFeedback(studentToken, problemId);
        JsonNode second = perform(post("/api/v1/admin/feedback/{id}/resolve", secondFeedback),
                fixedBody("A", "题干里的一个错字", true, "甲、乙相向而行，多少小时后相遇？"),
                adminToken, status().isOk());

        assertEquals(0, second.get("regradedCount").asInt(), "末端那条已经是答对，结果没变就不该再写一行");
        assertEquals(1, regradedCount(problemId), "原始提交不该被再重判一次");
        assertEquals(Map.of("attempt", 1, "correct", 1), progressOf(studentId));
    }

    @Test
    void fixedWithRegradeDisabledLeavesHistoryUntouched() throws Exception {
        long problemId = publishProblem("B");
        long oldVersionId = currentVersionId(problemId);
        long submissionId = submit(studentToken, problemId, oldVersionId, "A", "WRONG");
        long feedbackId = fileFeedback(studentToken, problemId);

        JsonNode resolved = perform(post("/api/v1/admin/feedback/{id}/resolve", feedbackId),
                fixedBody("A", "只订正题目，历史不动", false), adminToken, status().isOk());

        assertEquals("FIXED", resolved.get("status").asText());
        assertEquals(0, resolved.get("regradedCount").asInt());
        assertNull(regradedRowOrNull(submissionId));
        assertEquals("WRONG", resultOf(submissionId));
        assertEquals(1, wrongItemCount(studentId, problemId), "没勾重判就不该动错题本");
        assertEquals(Map.of("attempt", 1, "correct", 0), progressOf(studentId));
    }

    // ---------- 请求构造 ----------

    /** 三个选项的单选题，答案由参数给定。故意留出 C，用来制造「重判前后都错」的样本。 */
    private long publishProblem(String answerKey) throws Exception {
        String body = """
                {"title":"相遇问题 · 答案待订正","type":"SINGLE","difficulty":3,"grade":"六年级",
                 "stemMd":"甲乙相向而行，多少小时后相遇？",
                 "options":[{"key":"A","textMd":"4 小时"},{"key":"B","textMd":"5 小时"},{"key":"C","textMd":"6 小时"}],
                 "answerJson":{"choice":"%s"},"explanationMd":"见解析。","maxScore":100,
                 "tagIds":[%d],"source":{"originType":"ORIGINAL"},"publish":true}
                """.formatted(answerKey, TAG_ID);
        return perform(post("/api/v1/admin/problems"), body, adminToken, status().isOk()).get("id").asLong();
    }

    private String fixedBody(String answerKey, String changeNote, boolean regrade) {
        return fixedBody(answerKey, changeNote, regrade, "甲乙相向而行，多少小时后相遇？");
    }

    private String fixedBody(String answerKey, String changeNote, boolean regrade, String stem) {
        return """
                {"decision":"FIXED","regrade":%s,"remark":"确认答案录错",
                 "newVersion":{"title":"相遇问题 · 答案待订正","type":"SINGLE","difficulty":3,"grade":"六年级",
                  "stemMd":"%s",
                  "options":[{"key":"A","textMd":"4 小时"},{"key":"B","textMd":"5 小时"},{"key":"C","textMd":"6 小时"}],
                  "answerJson":{"choice":"%s"},"explanationMd":"见解析。","maxScore":100,
                  "tagIds":[%d],"source":{"originType":"ORIGINAL"},"changeNote":"%s","publish":true}}
                """.formatted(regrade, stem, answerKey, TAG_ID, changeNote);
    }

    private long fileFeedback(String token, long problemId) throws Exception {
        String body = """
                {"reason":"ANSWER_ERROR","detail":"标准答案应该是 A"}
                """;
        return perform(post("/api/v1/problems/{id}/feedback", problemId), body, token, status().isOk())
                .get("feedbackId").asLong();
    }

    private JsonNode rejectFeedback(long feedbackId, String remark) throws Exception {
        String body = """
                {"decision":"REJECTED","remark":"%s"}
                """.formatted(remark);
        return perform(post("/api/v1/admin/feedback/{id}/resolve", feedbackId), body, adminToken, status().isOk());
    }

    private long submit(String token, long problemId, long versionId, String choice, String expected)
            throws Exception {
        String body = """
                {"problemId":%d,"problemVersionId":%d,"answer":{"choice":"%s"},"durationMs":1500}
                """.formatted(problemId, versionId, choice);
        MvcResult result = mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        JsonNode submitted = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(expected, submitted.get("result").asText());
        return submitted.get("id").asLong();
    }

    private JsonNode adminQueue(String status) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/feedback").param("status", status)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ---------- 库内断言 ----------

    private long currentVersionId(long problemId) {
        Long id = jdbc.queryForObject(
                "SELECT current_version_id FROM problem WHERE id = ?", Long.class, problemId);
        return id == null ? 0L : id;
    }

    private int versionCount(long problemId) {
        return count("SELECT COUNT(1) FROM problem_version WHERE problem_id = ?", problemId);
    }

    private String resultOf(long submissionId) {
        return jdbc.queryForObject("SELECT result FROM submission WHERE id = ?", String.class, submissionId);
    }

    private Map<String, Object> regradedRow(long originalSubmissionId) {
        Map<String, Object> row = regradedRowOrNull(originalSubmissionId);
        assertTrue(row != null, "应该有一条 regraded_from 指向 " + originalSubmissionId + " 的新提交");
        return row;
    }

    private Map<String, Object> regradedRowOrNull(long originalSubmissionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, result, problem_version_id FROM submission WHERE regraded_from = ?",
                originalSubmissionId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int regradedCount(long problemId) {
        return count("SELECT COUNT(1) FROM submission WHERE problem_id = ? AND regraded_from IS NOT NULL",
                problemId);
    }

    private int wrongItemCount(long userId, long problemId) {
        return count("SELECT COUNT(1) FROM wrong_item WHERE user_id = ? AND problem_id = ?", userId, problemId);
    }

    /** 只取这道题挂的那个知识点，作答次数与正确数一起看才说明问题。 */
    private Map<String, Integer> progressOf(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT attempt_count, correct_count FROM user_tag_progress WHERE user_id = ? AND tag_id = ?
                """, userId, TAG_ID);
        if (rows.isEmpty()) {
            return Map.of("attempt", 0, "correct", 0);
        }
        Map<String, Object> row = rows.get(0);
        return Map.of("attempt", ((Number) row.get("attempt_count")).intValue(),
                "correct", ((Number) row.get("correct_count")).intValue());
    }

    private String statusOf(long feedbackId) {
        return jdbc.queryForObject("SELECT status FROM problem_feedback WHERE id = ?", String.class, feedbackId);
    }

    private long handledBy(long feedbackId) {
        Long id = jdbc.queryForObject(
                "SELECT handled_by FROM problem_feedback WHERE id = ?", Long.class, feedbackId);
        return id == null ? 0L : id;
    }

    private int auditCount(long feedbackId, String action) {
        return count("""
                SELECT COUNT(1) FROM audit_log
                 WHERE action = ? AND target_type = 'FEEDBACK' AND target_id = ?
                """, action, feedbackId);
    }

    private int count(String sql, Object... args) {
        Integer rows = jdbc.queryForObject(sql, Integer.class, args);
        return rows == null ? 0 : rows;
    }

    // ---------- 通用 ----------

    private static JsonNode findById(JsonNode array, long id) {
        JsonNode found = findByIdOrNull(array, id);
        assertTrue(found != null, "列表里应该有 id=" + id);
        return found;
    }

    private static JsonNode findByIdOrNull(JsonNode array, long id) {
        for (JsonNode item : array) {
            if (item.get("id").asLong() == id) {
                return item;
            }
        }
        return null;
    }

    private String registerToken(String nickname) throws Exception {
        String body = """
                {"nickname":"%s","email":"%s","password":"demo12345"}
                """.formatted(nickname, "feedback-" + UUID.randomUUID() + "@example.com");
        return perform(post("/api/v1/auth/register"), body, null, status().isOk()).get("accessToken").asText();
    }

    private long userIdOf(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode perform(MockHttpServletRequestBuilder request, String body, String token,
                             ResultMatcher expected) throws Exception {
        request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(request).andExpect(expected).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
