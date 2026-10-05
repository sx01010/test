package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 真实 MySQL 上的冒烟测试，只覆盖 H2 与 MySQL 行为确实不同的那几处：字符串转义、
 * JSON 列、以及 problem_source 的 upsert。其余业务逻辑由默认 profile 下的测试负责，
 * 这里不重复一遍。
 *
 * <p>默认整类跳过：没设 DB_PASSWORD 就不跑，别人 clone 下来 {@code mvn test} 不该因为
 * 缺一个本地数据库而失败。跑法：
 *
 * <pre>$env:DB_PASSWORD='math_dev_2026'; mvn -o -B test -Dtest=MySqlProfileSmokeTest</pre>
 *
 * <p>注意这个测试会往开发库里写数据：每跑一次多两个注册用户和一道草稿题。草稿不会出现在
 * 公开题库里，所以不会干扰人工验收时看到的题目列表。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+",
        disabledReason = "只在配好本地 MySQL 且设置了 DB_PASSWORD 时运行")
class MySqlProfileSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * MySQL 的字符串字面量把反斜杠当转义符，所以种子脚本里的 LaTeX 必须写成 {@code \\}。
     * 漏掉这一步的话，{@code \b} 会变成退格符、{@code \(} 的反斜杠被直接吞掉，公式当场报废。
     * H2 不做这层转义，这个坑只有真跑一次 MySQL 才会暴露。
     */
    @Test
    void seedSurvivesMySqlBackslashEscaping() {
        assertTrue(count("tag") >= 6, "六个知识点应该都在库里");
        assertTrue(count("problem") >= 5, "五道示例题应该都在库里");

        String judgeStem = jdbc.queryForObject(
                "SELECT stem_md FROM problem_version WHERE problem_id = 4 AND version_no = 1", String.class);
        assertTrue(judgeStem.contains("\\(\\alpha:\\beta:\\gamma = 1:2:3\\)"),
                "判断题的行内公式应原样落库，实际存的是：" + judgeStem);
        assertFalse(judgeStem.contains("\b"), "出现退格符，说明 \\b 被 MySQL 当成转义序列吃掉了");

        String travelExplanation = jdbc.queryForObject(
                "SELECT explanation_md FROM problem_version WHERE problem_id = 1 AND version_no = 1", String.class);
        assertTrue(travelExplanation.contains("\\(t = 4\\)"), "解析里的行内公式应原样落库");
    }

    /** answer_json 与判题明细在 MySQL 上是 JSON 列，在 H2 上是 CLOB，来回一趟确认两边都读得回来。 */
    @Test
    void submissionRoundTripsJsonColumnsOnRealMySql() throws Exception {
        String token = registerToken();

        JsonNode correct = submit(token, 1, currentVersionId(1), "{\"choice\":\"B\"}");
        assertEquals("CORRECT", correct.get("result").asText());

        mockMvc.perform(get("/api/v1/submissions/{id}", correct.get("id").asLong())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer.choice").value("B"))
                .andExpect(jsonPath("$.result").value("CORRECT"));

        // 答错一道多选，顺带验错题本：wrong_item 的唯一性两边靠的机制不一样
        JsonNode wrong = submit(token, 3, currentVersionId(3), "{\"choices\":[\"A\",\"B\"]}");
        assertEquals("WRONG", wrong.get("result").asText());

        mockMvc.perform(get("/api/v1/me/wrong-items").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].problemId").value(3));
    }

    /**
     * 管理端录入在 MySQL 上第一次真跑。problem_source 走「先 UPDATE 看影响行数再 INSERT」，
     * 避开了 MySQL 的 ON DUPLICATE KEY 与 H2 的 MERGE，这条路径值得在真库上确认一次。
     */
    @Test
    void adminUpsertWritesSourceAndBumpsVersionOnRealMySql() throws Exception {
        String token = registerToken();
        jdbc.update("UPDATE `user` SET role = 'ADMIN' WHERE id = ?", userId(token));

        JsonNode created = adminDraft(token, null, "初始题干", null);
        long id = created.get("id").asLong();
        assertEquals(1, created.get("versionNo").asInt());

        JsonNode revised = adminDraft(token, id, "改过的题干", "验证升版");
        assertEquals(2, revised.get("versionNo").asInt(), "题干变了就该升版");

        assertEquals(1, countWhere("problem_source", "problem_id = " + id), "来源登记只该有一条，不能被写成两条");
        assertEquals(2, countWhere("problem_version", "problem_id = " + id));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM problem WHERE id = ?", String.class, id));
    }

    /**
     * V9 先 DROP CHECK 再加回放宽后的约束。MySQL 8.0.16 起才真正执行 CHECK，H2 的语法也不同，
     * 注销把 email、phone 都置空正好踩在这条约束上。
     */
    @Test
    void accountDeletionPassesRelaxedContactCheckOnRealMySql() throws Exception {
        String token = registerToken();
        long id = userId(token);

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"demo12345\"}"))
                .andExpect(status().isNoContent());
        assertEquals("DELETED", jdbc.queryForObject("SELECT status FROM `user` WHERE id = ?", String.class, id));

        // 约束仍然拦得住未注销却没有联系方式的行。MySQL 的 3819 Spring 没做分类，只能认约束名
        DataAccessException violated = assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO `user` (nickname, password_hash, role, status) VALUES ('x', 'x', 'USER', 'ACTIVE')"));
        assertTrue(violated.getMessage().contains("ck_user_contact"), violated.getMessage());
    }

    @Test
    void assetUploadRoundTripsOnRealMySql() throws Exception {
        String token = registerToken();
        jdbc.update("UPDATE `user` SET role = 'ADMIN' WHERE id = ?", userId(token));
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

        MvcResult uploaded = mockMvc.perform(post("/api/v1/admin/assets").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(png))
                .andExpect(status().isOk()).andReturn();
        String key = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("key").asText();

        mockMvc.perform(get("/api/v1/assets/{key}", key))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    private int count(String table) {
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class);
        return rows == null ? 0 : rows;
    }

    private int countWhere(String table, String where) {
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "` WHERE " + where, Integer.class);
        return rows == null ? 0 : rows;
    }

    private long currentVersionId(long problemId) {
        Long versionId = jdbc.queryForObject(
                "SELECT current_version_id FROM problem WHERE id = ?", Long.class, problemId);
        return versionId == null ? 0L : versionId;
    }

    private JsonNode submit(String token, long problemId, long versionId, String answer) throws Exception {
        String body = """
                {"problemId":%d,"problemVersionId":%d,"answer":%s,"durationMs":1200}
                """.formatted(problemId, versionId, answer);
        MvcResult result = mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode adminDraft(String token, Long id, String stem, String changeNote) throws Exception {
        String body = """
                {%s"title":"MySQL 冒烟草稿","type":"SINGLE","difficulty":3,"grade":"六年级","stemMd":"%s",
                 "options":[{"key":"A","textMd":"12 小时"},{"key":"B","textMd":"24 小时"}],
                 "answerJson":{"choice":"B"},"explanationMd":"因为 24。","tagIds":[1],
                 "source":{"originType":"ORIGINAL"},%s"publish":false}
                """.formatted(
                id == null ? "" : "\"id\":" + id + ",",
                stem,
                changeNote == null ? "" : "\"changeNote\":\"" + changeNote + "\",");
        MvcResult result = mockMvc.perform(post("/api/v1/admin/problems")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String registerToken() throws Exception {
        String body = """
                {"nickname":"冒烟","email":"%s","password":"demo12345"}
                """.formatted("mysql-smoke-" + UUID.randomUUID() + "@example.com");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long userId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
