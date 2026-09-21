package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * R20 找回密码。
 *
 * <p>验证码送不出去（没有邮件或短信通道），所以测试直接从库里把那一行读出来、
 * 用已知的候选码去比对哈希。这同时顺带验证了「库里存的不是明文」。
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:password-reset;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "NON_KEYWORDS=USER,YEAR,VALUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

    private static final String OLD_PASSWORD = "demo12345";
    private static final String NEW_PASSWORD = "brandnew9876";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String email;
    private long userId;
    private String refreshToken;

    @BeforeEach
    void registerAccount() throws Exception {
        email = "reset-" + UUID.randomUUID() + "@example.com";
        JsonNode issued = perform(post("/api/v1/auth/register"), """
                {"nickname":"忘密码的人","email":"%s","password":"%s"}
                """.formatted(email, OLD_PASSWORD), status().isOk());
        userId = issued.get("userId").asLong();
        refreshToken = issued.get("refreshToken").asText();
    }

    @Test
    void requestingCodeLooksIdenticalWhetherTheAccountExistsOrNot() throws Exception {
        String existing = requestCodeRaw(email);
        int rowsAfterRealAccount = totalCodeRows();
        String missing = requestCodeRaw("nobody-" + UUID.randomUUID() + "@example.com");

        // 一个「该邮箱未注册」的提示就把接口变成了账号枚举器
        assertEquals(existing, missing, "两种情况的响应必须一模一样");
        assertEquals(rowsAfterRealAccount, totalCodeRows(), "不存在的账号不该留下任何验证码行");
    }

    @Test
    void cooldownSuppressesTheSecondCodeWithoutChangingTheResponse() throws Exception {
        String first = requestCodeRaw(email);
        String second = requestCodeRaw(email);

        assertEquals(first, second, "冷却期内的响应也不能有差别，否则又暴露了账号存在");
        assertEquals(1, codeRowCount(userId), "一分钟内只签发一条");
    }

    @Test
    void codeResetsPasswordAndRotatesEverySession() throws Exception {
        String code = issueCodeKnownToTest("246813");

        JsonNode reset = perform(post("/api/v1/auth/password/reset"), resetBody(email, code, NEW_PASSWORD),
                status().isOk());
        assertEquals(userId, reset.get("userId").asLong());
        assertNotNull(reset.get("accessToken").asText(), "重置即登录，用户刚证明了自己控制这个邮箱");

        // 新密码能登，旧密码不能
        perform(post("/api/v1/auth/login"), loginBody(email, NEW_PASSWORD), status().isOk());
        perform(post("/api/v1/auth/login"), loginBody(email, OLD_PASSWORD), status().isUnauthorized());

        // 改密码的常见场景是「号被别人登进去了」，不踢掉旧会话等于没改
        perform(post("/api/v1/auth/refresh"), """
                {"refreshToken":"%s"}
                """.formatted(refreshToken), status().isUnauthorized());

        // 刚签发的那对令牌本身要可用
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + reset.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void lockedAccountCanGetBackInRightAfterReset() throws Exception {
        // 连续失败 5 次触发锁定（mathematics.auth.max-login-failures）
        for (int attempt = 0; attempt < 5; attempt++) {
            perform(post("/api/v1/auth/login"), loginBody(email, "wrongpassword"), status().is4xxClientError());
        }
        assertEquals("LOCKED", jdbc.queryForObject(
                "SELECT status FROM `user` WHERE id = ?", String.class, userId));

        String code = issueCodeKnownToTest("246813");
        perform(post("/api/v1/auth/password/reset"), resetBody(email, code, NEW_PASSWORD), status().isOk());

        // 忘密码的人往往已经试错到被锁，重置完还进不来就白做了
        perform(post("/api/v1/auth/login"), loginBody(email, NEW_PASSWORD), status().isOk());
        assertEquals("ACTIVE", jdbc.queryForObject(
                "SELECT status FROM `user` WHERE id = ?", String.class, userId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT fail_count FROM `user` WHERE id = ?", Integer.class, userId));
    }

    @Test
    void codeDiesAfterFiveWrongGuesses() throws Exception {
        String code = issueCodeKnownToTest("246813");

        for (int attempt = 0; attempt < 5; attempt++) {
            perform(post("/api/v1/auth/password/reset"), resetBody(email, "999999", NEW_PASSWORD),
                    status().isBadRequest());
        }

        // 六位数字能无限猜的话，平均五十万次就中，10 分钟内轮询完全做得到
        perform(post("/api/v1/auth/password/reset"), resetBody(email, code, NEW_PASSWORD),
                status().isBadRequest());
        perform(post("/api/v1/auth/login"), loginBody(email, OLD_PASSWORD), status().isOk());
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        String code = issueCodeKnownToTest("246813");
        jdbc.update("UPDATE password_reset_code SET expires_at = DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) "
                + "WHERE user_id = ?", userId);

        perform(post("/api/v1/auth/password/reset"), resetBody(email, code, NEW_PASSWORD),
                status().isBadRequest());
        perform(post("/api/v1/auth/login"), loginBody(email, OLD_PASSWORD), status().isOk());
    }

    @Test
    void codeCannotBeUsedTwice() throws Exception {
        String code = issueCodeKnownToTest("246813");
        perform(post("/api/v1/auth/password/reset"), resetBody(email, code, NEW_PASSWORD), status().isOk());

        perform(post("/api/v1/auth/password/reset"), resetBody(email, code, "yetanother777"),
                status().isBadRequest());
        perform(post("/api/v1/auth/login"), loginBody(email, NEW_PASSWORD), status().isOk());
    }

    @Test
    void issuingANewCodeKillsTheOldOne() throws Exception {
        String first = issueCodeKnownToTest("111222");
        String second = issueCodeKnownToTest("333444");

        // 连点三次就有三个码同时有效，暴破面积直接乘三
        perform(post("/api/v1/auth/password/reset"), resetBody(email, first, NEW_PASSWORD),
                status().isBadRequest());
        perform(post("/api/v1/auth/password/reset"), resetBody(email, second, NEW_PASSWORD), status().isOk());
    }

    /** 这条不借测试改写哈希，检查真实生成路径的产物。 */
    @Test
    void codeIsNeverStoredInPlainTextAndNeverReturnedInTheResponse() throws Exception {
        String body = requestCodeRaw(email);

        // 放进响应体的话，任何人都能拿别人的邮箱要一个码然后直接改密码
        assertFalse(body.matches(".*\\d{6}.*"), "响应体里不能出现任何六位数字：" + body);

        String stored = jdbc.queryForObject(
                "SELECT code_hash FROM password_reset_code WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                String.class, userId);
        // 库泄露时明文验证码等于直接交出账号
        assertTrue(stored.startsWith("$2"), "库里存的必须是 BCrypt 哈希，实际是：" + stored);
        assertFalse(stored.matches(".*\\b\\d{6}\\b.*"), "哈希里不该夹着明文验证码");
    }

    // ---------- 辅助 ----------

    /**
     * 验证码送不出去（没有邮件或短信通道），而穷举 BCrypt 反解六位数字要跑几小时。
     * 所以让测试来定这个码：正常申请一次，再把库里那一行的哈希换成已知值。
     *
     * <p>这样绕过的只有「码是怎么生成的」，冷却、过期、次数、一次性、旧码作废、会话轮换
     * 全都走真实路径。生成本身由
     * {@link #codeIsNeverStoredInPlainTextAndNeverReturnedInTheResponse()} 单独把关。
     */
    private String issueCodeKnownToTest(String code) throws Exception {
        expireCooldown();
        requestCodeRaw(email);
        jdbc.update("""
                UPDATE password_reset_code SET code_hash = ?
                 WHERE id = (SELECT MAX(id) FROM password_reset_code WHERE user_id = ?)
                """, passwordEncoder.encode(code), userId);
        return code;
    }

    private void expireCooldown() {
        jdbc.update("UPDATE password_reset_code SET created_at = DATEADD('MINUTE', -2, created_at) "
                + "WHERE user_id = ?", userId);
    }

    private String requestCodeRaw(String account) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/password/reset-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + account + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getContentAsString();
    }

    private int codeRowCount(long forUserId) {
        return jdbc.queryForObject("SELECT COUNT(1) FROM password_reset_code WHERE user_id = ?",
                Integer.class, forUserId);
    }

    /** 整个类共用一个内存库，前面测试留下的行还在，所以只能比差值不能比绝对数。 */
    private int totalCodeRows() {
        return jdbc.queryForObject("SELECT COUNT(1) FROM password_reset_code", Integer.class);
    }

    private static String resetBody(String account, String code, String newPassword) {
        return """
                {"account":"%s","code":"%s","newPassword":"%s"}
                """.formatted(account, code, newPassword);
    }

    private static String loginBody(String account, String password) {
        return """
                {"account":"%s","password":"%s"}
                """.formatted(account, password);
    }

    private JsonNode perform(MockHttpServletRequestBuilder request, String body, ResultMatcher expected)
            throws Exception {
        MvcResult result = mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(expected).andReturn();
        String payload = result.getResponse().getContentAsString();
        return payload.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(payload);
    }
}
