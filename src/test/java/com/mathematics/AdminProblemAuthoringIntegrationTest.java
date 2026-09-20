package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * R16–R17：管理端录入、来源闸门、版本快照与审计。
 *
 * <p>这里刻意换一个 H2 库名：默认库是 {@link PracticeFlowIntegrationTest} 在用的，
 * 那边有「SINGLE,MULTI 恰好 2 条」这类精确条数断言，本类一发布新题就会把它打挂。
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:admin-authoring;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "NON_KEYWORDS=USER,YEAR,VALUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AdminProblemAuthoringIntegrationTest {

    private static final String ADAPTED_INCOMPLETE = """
            {"originType":"ADAPTED","contestName":"迎春杯","year":2021,"round":"初赛"}
            """;
    private static final String ADAPTED_COMPLETE = """
            {"originType":"ADAPTED","contestName":"迎春杯","year":2021,"round":"初赛",
             "rewriteNote":"参考考点重新命题，换掉全部数值并自写解析。"}
            """;
    private static final String ORIGINAL = """
            {"originType":"ORIGINAL"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void prepareAccounts() throws Exception {
        adminToken = registerToken("录入员");
        jdbc.update("UPDATE `user` SET role = 'ADMIN' WHERE id = ?", userIdOf(adminToken));
        userToken = registerToken("学生");
    }

    @Test
    void adminEndpointsRejectAnonymousAndNonAdmin() throws Exception {
        String body = singleChoiceBody(null, "越权尝试", "题干", ORIGINAL, null, false);

        perform(post("/api/v1/admin/problems"), body, null, status().isUnauthorized());
        JsonNode forbidden = perform(post("/api/v1/admin/problems"), body, userToken, status().isForbidden());
        assertEquals("FORBIDDEN", forbidden.get("code").asText());

        mockMvc.perform(get("/api/v1/admin/problems").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void incompleteSourceBlocksPublishButDraftStillSaves() throws Exception {
        // ADAPTED 少了 rewriteNote，直接发布要被来源闸门拦住
        JsonNode rejected = perform(post("/api/v1/admin/problems"),
                singleChoiceBody(null, "改编题", "题干", ADAPTED_INCOMPLETE, null, true),
                adminToken, status().isBadRequest());
        assertEquals("VALIDATION_ERROR", rejected.get("code").asText());

        // 同样的内容存草稿是允许的：先录题、后补授权是真实的工作顺序
        JsonNode draft = perform(post("/api/v1/admin/problems"),
                singleChoiceBody(null, "改编题", "题干", ADAPTED_INCOMPLETE, null, false),
                adminToken, status().isOk());
        assertEquals("DRAFT", draft.get("status").asText());
        assertEquals(1, draft.get("versionNo").asInt());

        long id = draft.get("id").asLong();
        JsonNode drafts = adminList("DRAFT");
        assertTrue(containsId(drafts, id), "草稿要出现在 status=DRAFT 的管理端列表里");
        for (JsonNode item : drafts) {
            assertEquals("DRAFT", item.get("status").asText(), "status 筛选不该混入其它状态");
        }

        // 草稿不能被公开接口看到
        mockMvc.perform(get("/api/v1/problems/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void publishWritesAuditAndExposesProblemPublicly() throws Exception {
        long id = perform(post("/api/v1/admin/problems"),
                singleChoiceBody(null, "补齐来源后发布", "甲每小时行 8 千米。", ADAPTED_INCOMPLETE, null, false),
                adminToken, status().isOk()).get("id").asLong();

        JsonNode published = perform(post("/api/v1/admin/problems"),
                singleChoiceBody(id, "补齐来源后发布", "甲每小时行 8 千米。", ADAPTED_COMPLETE, null, true),
                adminToken, status().isOk());
        assertEquals("PUBLISHED", published.get("status").asText());
        assertEquals(1, published.get("versionNo").asInt(), "只补来源不该升版");

        mockMvc.perform(get("/api/v1/problems/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("补齐来源后发布"))
                .andExpect(jsonPath("$.source.originType").value("ADAPTED"))
                .andExpect(jsonPath("$.source.rewriteNote").doesNotExist())
                .andExpect(jsonPath("$.answerJson").doesNotExist());

        assertEquals(1, auditCount(id), "发布要留一条 PROBLEM_PUBLISH");
    }

    @Test
    void headOnlyEditKeepsVersionAndContentEditBumpsIt() throws Exception {
        long id = publishedProblem("原标题", "原题干");

        // 只改标题：不升版，历史提交不该被标成「题目已更新」
        JsonNode renamed = perform(post("/api/v1/admin/problems"),
                singleChoiceBody(id, "新标题", "原题干", ADAPTED_COMPLETE, null, true),
                adminToken, status().isOk());
        assertEquals(1, renamed.get("versionNo").asInt());
        assertEquals(1, versionCount(id));

        // 改题干：升版并指向新版本
        JsonNode revised = perform(post("/api/v1/admin/problems"),
                singleChoiceBody(id, "新标题", "订正后的题干", ADAPTED_COMPLETE, "订正题干笔误", true),
                adminToken, status().isOk());
        assertEquals(2, revised.get("versionNo").asInt());
        assertEquals(2, versionCount(id));

        mockMvc.perform(get("/api/v1/problems/{id}", id))
                .andExpect(jsonPath("$.title").value("新标题"))
                .andExpect(jsonPath("$.stemMd").value("订正后的题干"))
                .andExpect(jsonPath("$.versionNo").value(2));
    }

    @Test
    void publishedContentChangeRequiresChangeNote() throws Exception {
        long id = publishedProblem("要订正的题", "原题干");

        JsonNode rejected = perform(post("/api/v1/admin/problems"),
                singleChoiceBody(id, "要订正的题", "改过的题干", ADAPTED_COMPLETE, null, true),
                adminToken, status().isBadRequest());
        assertEquals("VALIDATION_ERROR", rejected.get("code").asText());
        assertEquals(1, versionCount(id), "被拒的请求不该留下半条版本");
    }

    @Test
    void answerMustPassGraderSelfCheck() throws Exception {
        // 答案选了 E，但选项只有 A、B
        String badKey = """
                {"title":"答案越界","type":"SINGLE","difficulty":3,"grade":"六年级","stemMd":"题干",
                 "options":[{"key":"A","textMd":"12"},{"key":"B","textMd":"24"}],
                 "answerJson":{"choice":"E"},"explanationMd":"解析","tagIds":[1],"source":%s}
                """.formatted(ORIGINAL);
        perform(post("/api/v1/admin/problems"), badKey, adminToken, status().isBadRequest());

        // 多空题的标准答案必须是「每空一组可接受写法」，空数组读不出答案
        String badBlank = """
                {"title":"空答案","type":"BLANK","difficulty":3,"grade":"六年级","stemMd":"题干",
                 "answerJson":{"blanks":[]},"explanationMd":"解析","tagIds":[1],"source":%s}
                """.formatted(ORIGINAL);
        perform(post("/api/v1/admin/problems"), badBlank, adminToken, status().isBadRequest());

        // 选择题至少要有两个选项
        String oneOption = """
                {"title":"只有一个选项","type":"SINGLE","difficulty":3,"grade":"六年级","stemMd":"题干",
                 "options":[{"key":"A","textMd":"12"}],
                 "answerJson":{"choice":"A"},"explanationMd":"解析","tagIds":[1],"source":%s}
                """.formatted(ORIGINAL);
        perform(post("/api/v1/admin/problems"), oneOption, adminToken, status().isBadRequest());
    }

    @Test
    void multiBlankProblemAcceptsPerBlankAliases() throws Exception {
        String body = """
                {"title":"多空题录入","type":"BLANK","difficulty":3,"grade":"六年级",
                 "stemMd":"求两个三角形的面积。",
                 "answerJson":{"blanks":[["16","十六"],["32"]]},
                 "graderConfig":{"orderIndependent":false},
                 "explanationMd":"16 与 32。","tagIds":[1],"source":%s,"publish":true}
                """.formatted(ORIGINAL);

        long id = perform(post("/api/v1/admin/problems"), body, adminToken, status().isOk()).get("id").asLong();

        mockMvc.perform(get("/api/v1/problems/{id}", id))
                .andExpect(jsonPath("$.type").value("BLANK"))
                .andExpect(jsonPath("$.blankCount").value(2))
                .andExpect(jsonPath("$.options").doesNotExist());
    }

    @Test
    void unknownTagIsRejected() throws Exception {
        String body = """
                {"title":"知识点不存在","type":"SINGLE","difficulty":3,"grade":"六年级","stemMd":"题干",
                 "options":[{"key":"A","textMd":"12"},{"key":"B","textMd":"24"}],
                 "answerJson":{"choice":"B"},"explanationMd":"解析","tagIds":[9999],"source":%s}
                """.formatted(ORIGINAL);

        perform(post("/api/v1/admin/problems"), body, adminToken, status().isBadRequest());
    }

    @Test
    void adminDetailCarriesAnswerAndInternalSourceForEditing() throws Exception {
        long id = publishedProblem("可编辑详情", "题干");

        mockMvc.perform(get("/api/v1/admin/problems/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.answerJson.choice").value("B"))
                .andExpect(jsonPath("$.explanationMd").exists())
                .andExpect(jsonPath("$.options[0].key").value("A"))
                .andExpect(jsonPath("$.tagIds[0]").value(1))
                .andExpect(jsonPath("$.source.rewriteNote").exists())
                .andExpect(jsonPath("$.versionNo").value(1));
    }

    private long publishedProblem(String title, String stem) throws Exception {
        return perform(post("/api/v1/admin/problems"),
                singleChoiceBody(null, title, stem, ADAPTED_COMPLETE, null, true),
                adminToken, status().isOk()).get("id").asLong();
    }

    private String singleChoiceBody(Long id, String title, String stem, String source,
                                    String changeNote, boolean publish) {
        return """
                {%s"title":"%s","type":"SINGLE","difficulty":3,"grade":"六年级","stemMd":"%s",
                 "options":[{"key":"A","textMd":"12 小时"},{"key":"B","textMd":"24 小时"}],
                 "answerJson":{"choice":"B"},"explanationMd":"因为 24。","maxScore":100,
                 "tagIds":[1],"source":%s,%s"publish":%s}
                """.formatted(
                id == null ? "" : "\"id\":" + id + ",",
                title, stem, source,
                changeNote == null ? "" : "\"changeNote\":\"" + changeNote + "\",",
                publish);
    }

    private JsonNode adminList(String status) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/problems").param("status", status)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static boolean containsId(JsonNode array, long id) {
        for (JsonNode item : array) {
            if (item.get("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }

    private int versionCount(long problemId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM problem_version WHERE problem_id = ?", Integer.class, problemId);
        return count == null ? 0 : count;
    }

    private int auditCount(long problemId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM audit_log
                 WHERE action = 'PROBLEM_PUBLISH' AND target_type = 'PROBLEM' AND target_id = ?
                """, Integer.class, problemId);
        return count == null ? 0 : count;
    }

    private String registerToken(String nickname) throws Exception {
        String body = """
                {"nickname":"%s","email":"%s","password":"demo12345"}
                """.formatted(nickname, "admin-" + UUID.randomUUID() + "@example.com");
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
