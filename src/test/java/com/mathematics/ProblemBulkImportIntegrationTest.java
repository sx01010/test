package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
 * R19 批量导入。
 *
 * <p>最关键的一条是 {@link #badItemInTheMiddleDoesNotRollBackTheGoodOnes()}：坏条目夹在中间时，
 * 它前后的好条目必须真的留在库里。编排方法一旦误加 {@code @Transactional}，
 * 所有 upsert 会并进同一个事务，第一条失败就把整批标成 rollback-only——
 * 接口照样返回「成功 2 条」，库里一条都没有。响应对、数据空，是最难发现的那种错。
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:bulk-import;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "NON_KEYWORDS=USER,YEAR,VALUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ProblemBulkImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void prepareAccounts() throws Exception {
        adminToken = registerToken("导入员");
        jdbc.update("UPDATE `user` SET role = 'ADMIN' WHERE id = ?", userIdOf(adminToken));
        studentToken = registerToken("学生");
    }

    @Test
    void importIsAdminOnly() throws Exception {
        String body = importBody(item("越权尝试", "B", true));

        perform(post("/api/v1/admin/problems/import"), body, null, status().isUnauthorized());
        JsonNode forbidden = perform(post("/api/v1/admin/problems/import"), body,
                studentToken, status().isForbidden());
        assertEquals("FORBIDDEN", forbidden.get("code").asText());
    }

    /** 给内容组的模板必须能原样导入：格式一改、模板没跟着改，这里先红。 */
    @Test
    void contentTemplateImportsCleanlyWithTagSlugs() throws Exception {
        String template = Files.readString(Path.of("docs/content/problem-import-template.json"));
        JsonNode result = perform(post("/api/v1/admin/problems/import"), template, adminToken, status().isOk());

        assertEquals(5, result.get("succeeded").asInt(), result.toString());
        assertEquals(0, result.get("failed").size(), result.toString());
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM problem_tag pt JOIN problem p ON p.id = pt.problem_id
                 WHERE p.title = '裂项求和 · 到 9×10'
                """, Integer.class), "两个 slug 都应解析成知识点");
    }

    @Test
    void unknownTagSlugFailsThatLineOnly() throws Exception {
        String bad = item("坏 slug", "B", false).replace("\"tagIds\":[1]", "\"tagSlugs\":[\"no-such-tag\"]");
        JsonNode result = perform(post("/api/v1/admin/problems/import"),
                importBody(bad, item("slug 的好邻居", "A", false)), adminToken, status().isOk());
        assertEquals(1, result.get("succeeded").asInt());
        assertTrue(result.get("failed").get(0).get("reason").asText().contains("no-such-tag"));
    }

    @Test
    void badItemInTheMiddleDoesNotRollBackTheGoodOnes() throws Exception {
        // 第 2 条的答案选了 E，选项里只有 A、B，答案自检会拒掉它
        String body = importBody(
                item("导入题一", "B", true),
                item("导入题二", "E", true),
                item("导入题三", "A", false));

        JsonNode result = perform(post("/api/v1/admin/problems/import"), body, adminToken, status().isOk());

        assertEquals(2, result.get("succeeded").asInt());
        assertEquals(1, result.get("failed").size());
        assertEquals(2, result.get("failed").get(0).get("line").asInt(), "行号从 1 开始数");
        assertTrue(result.get("failed").get(0).get("reason").asText().length() > 0, "失败行要说明原因");

        // 这三条断言才是重点：好条目真的落库了，没被后面那条失败带走
        assertEquals(1, countByTitle("导入题一"));
        assertEquals(0, countByTitle("导入题二"));
        assertEquals(1, countByTitle("导入题三"));
        assertEquals("PUBLISHED", statusByTitle("导入题一"));
        assertEquals("DRAFT", statusByTitle("导入题三"), "publish 是逐条决定的");

        // 发布出去的那条公开接口能查到，说明走的是和单条录入同一条路
        mockMvc.perform(get("/api/v1/problems").param("kw", "导入题一"))
                .andExpect(status().isOk());
    }

    @Test
    void itemsCarryingAnIdAreRejectedWithoutTouchingTheExistingProblem() throws Exception {
        // 先用单条接口建一道已发布的题
        long existing = perform(post("/api/v1/admin/problems"),
                item("原有题目", "B", true), adminToken, status().isOk()).get("id").asLong();

        String body = importBody(item("原有题目", "B", true).replace("{\"title\"", "{\"id\":" + existing + ",\"title\""));
        JsonNode result = perform(post("/api/v1/admin/problems/import"), body, adminToken, status().isOk());

        assertEquals(0, result.get("succeeded").asInt());
        assertEquals(1, result.get("failed").size());
        assertTrue(result.get("failed").get(0).get("reason").asText().contains("id"),
                "原因要点明是 id 的问题，实际是：" + result.get("failed").get(0).get("reason").asText());

        // 导入只新建。混进 id 就静默覆盖线上题目并升版，那是导入这个动作不该有的能力
        assertEquals(1, versionCount(existing), "被拒的条目不该给原题升版");
        assertEquals(1, countByTitle("原有题目"));
    }

    @Test
    void perLineValidationReportsFieldErrorsInsteadOfFailingTheWholeBatch() throws Exception {
        // 第 1 条缺解析（字段级校验），第 2 条完全合法
        String missingExplanation = """
                {"title":"缺解析","type":"SINGLE","difficulty":3,"grade":"六年级","stemMd":"题干",
                 "options":[{"key":"A","textMd":"12"},{"key":"B","textMd":"24"}],
                 "answerJson":{"choice":"B"},"explanationMd":"","tagIds":[1],
                 "source":{"originType":"ORIGINAL"},"publish":false}
                """;
        JsonNode result = perform(post("/api/v1/admin/problems/import"),
                importBody(missingExplanation, item("字段校验的好邻居", "A", false)),
                adminToken, status().isOk());

        // 字段级校验也要落到行：整个请求带 @Valid 级联的话这里会是一个 400，好邻居也进不去
        assertEquals(1, result.get("succeeded").asInt());
        assertEquals(1, result.get("failed").size());
        assertEquals(1, result.get("failed").get(0).get("line").asInt());
        assertEquals(1, countByTitle("字段校验的好邻居"));
        assertEquals(0, countByTitle("缺解析"));
    }

    @Test
    void sourceGateStillAppliesOnTheImportPath() throws Exception {
        // ADAPTED 少了 rewriteNote，发布要被来源闸门拦住；存草稿是允许的
        String adapted = """
                {"title":"改编导入题","type":"SINGLE","difficulty":3,"grade":"六年级","stemMd":"题干",
                 "options":[{"key":"A","textMd":"12"},{"key":"B","textMd":"24"}],
                 "answerJson":{"choice":"B"},"explanationMd":"解析","tagIds":[1],
                 "source":{"originType":"ADAPTED","contestName":"迎春杯","year":2021},"publish":%s}
                """;

        JsonNode published = perform(post("/api/v1/admin/problems/import"),
                importBody(adapted.formatted("true")), adminToken, status().isOk());
        assertEquals(0, published.get("succeeded").asInt());
        assertEquals(0, countByTitle("改编导入题"));

        JsonNode draft = perform(post("/api/v1/admin/problems/import"),
                importBody(adapted.formatted("false")), adminToken, status().isOk());
        assertEquals(1, draft.get("succeeded").asInt());
        assertEquals("DRAFT", statusByTitle("改编导入题"));
    }

    @Test
    void emptyAndOversizeBatchesAreRejectedUpFront() throws Exception {
        perform(post("/api/v1/admin/problems/import"), """
                {"items":[]}
                """, adminToken, status().isBadRequest());

        // 201 条。不设上限的话一个请求能占着数据库连接跑很久
        String tooMany = IntStream.rangeClosed(1, 201)
                .mapToObj(index -> item("批量第 " + index + " 题", "A", false))
                .collect(Collectors.joining(","));
        perform(post("/api/v1/admin/problems/import"), "{\"items\":[" + tooMany + "]}",
                adminToken, status().isBadRequest());

        assertEquals(0, countByTitle("批量第 1 题"), "整批被拒时不该有任何一条溜进去");
    }

    @Test
    void importWritesOneAuditRowForTheWholeBatch() throws Exception {
        perform(post("/api/v1/admin/problems/import"),
                importBody(item("审计题一", "A", false), item("审计题二", "E", false)),
                adminToken, status().isOk());

        Integer rows = jdbc.queryForObject("""
                SELECT COUNT(1) FROM audit_log WHERE action = 'PROBLEM_IMPORT' AND actor_id = ?
                """, Integer.class, userIdOf(adminToken));
        assertEquals(1, rows, "整批只记一条，逐条发布本来各有一条 PROBLEM_PUBLISH");
    }

    // ---------- 请求构造 ----------

    private static String item(String title, String answerKey, boolean publish) {
        return """
                {"title":"%s","type":"SINGLE","difficulty":3,"grade":"六年级","stemMd":"甲乙相向而行，几小时相遇？",
                 "options":[{"key":"A","textMd":"12 小时"},{"key":"B","textMd":"24 小时"}],
                 "answerJson":{"choice":"%s"},"explanationMd":"因为 24。","maxScore":100,"tagIds":[1],
                 "source":{"originType":"ORIGINAL"},"publish":%s}
                """.formatted(title, answerKey, publish);
    }

    private static String importBody(String... items) {
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    // ---------- 库内断言 ----------

    private int countByTitle(String title) {
        Integer rows = jdbc.queryForObject("SELECT COUNT(1) FROM problem WHERE title = ?", Integer.class, title);
        return rows == null ? 0 : rows;
    }

    private String statusByTitle(String title) {
        return jdbc.queryForObject("SELECT status FROM problem WHERE title = ?", String.class, title);
    }

    private int versionCount(long problemId) {
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(1) FROM problem_version WHERE problem_id = ?", Integer.class, problemId);
        return rows == null ? 0 : rows;
    }

    // ---------- 通用 ----------

    private String registerToken(String nickname) throws Exception {
        String body = """
                {"nickname":"%s","email":"%s","password":"demo12345"}
                """.formatted(nickname, "import-" + UUID.randomUUID() + "@example.com");
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
        String payload = result.getResponse().getContentAsString();
        return payload.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(payload);
    }
}
