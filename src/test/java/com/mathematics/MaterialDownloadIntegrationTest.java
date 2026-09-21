package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
 * R21–R23：资料上传、发布、下载与下架。
 *
 * <p>文件写到 target 下的独立目录，库也用独立的 H2 名字，都不碰开发用的 data/。
 * 签名密钥钉死一个固定值，否则每次启动随机生成，测不出「令牌被改过就失效」。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:material-download;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "NON_KEYWORDS=USER,YEAR,VALUE;DB_CLOSE_DELAY=-1",
        "mathematics.material.storage-dir=./target/test-materials",
        "mathematics.material.signing-secret=test-only-material-signing-secret"})
@AutoConfigureMockMvc
class MaterialDownloadIntegrationTest {

    /** 最小的合法 PDF 开头。判断是不是 PDF 看魔数，不看扩展名。 */
    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\ntrailer\n%%EOF\n"
            .getBytes(StandardCharsets.UTF_8);

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
        adminToken = registerToken("资料管理员");
        jdbc.update("UPDATE `user` SET role = 'ADMIN' WHERE id = ?", userIdOf(adminToken));
        studentToken = registerToken("学生");
    }

    @Test
    void uploadTicketIsAdminOnly() throws Exception {
        String body = ticketBody("2023-真题.pdf", PDF.length, sha256(PDF));

        perform(post("/api/v1/admin/materials/upload-ticket"), body, null, status().isUnauthorized());
        JsonNode forbidden = perform(post("/api/v1/admin/materials/upload-ticket"), body,
                studentToken, status().isForbidden());
        assertEquals("FORBIDDEN", forbidden.get("code").asText());
    }

    @Test
    void ticketRejectsNonPdfAndOversizeBeforeAnyBytesMove() throws Exception {
        perform(post("/api/v1/admin/materials/upload-ticket"),
                ticketBody("讲义.docx", 1024, sha256(PDF)).replace("application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                adminToken, status().isBadRequest());

        // 31 MB，库里的 CHECK 约束上限是 30 MB，不能等到插库才发现
        perform(post("/api/v1/admin/materials/upload-ticket"),
                ticketBody("大文件.pdf", 32_505_856L, sha256(PDF)), adminToken, status().isBadRequest());

        // sha256 必须是 64 位十六进制
        perform(post("/api/v1/admin/materials/upload-ticket"),
                ticketBody("摘要不对.pdf", PDF.length, "zzzz"), adminToken, status().isBadRequest());
    }

    @Test
    void uploadRejectsTamperedTokenAndMismatchedBytes() throws Exception {
        JsonNode ticket = ticket("2023-真题.pdf", PDF);
        String uploadUrl = ticket.get("uploadUrl").asText();

        // 令牌改一个字节就该失效
        String tampered = uploadUrl.substring(0, uploadUrl.length() - 2)
                + (uploadUrl.endsWith("A") ? "B" : "A");
        upload(tampered, PDF, status().isForbidden());

        // 字节和凭证里声明的 sha256 对不上
        byte[] other = "%PDF-1.7\n完全不同的内容\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
        upload(uploadUrl, other, status().isBadRequest());

        // 声明的是 PDF，实际不是
        JsonNode fakeTicket = ticket("伪装.pdf", "MZ\u0000\u0000not a pdf".getBytes(StandardCharsets.UTF_8));
        upload(fakeTicket.get("uploadUrl").asText(),
                "MZ\u0000\u0000not a pdf".getBytes(StandardCharsets.UTF_8), status().isBadRequest());

        // 原始凭证配原始字节仍然能过，说明上面拒的是篡改而不是接口本身不通
        upload(uploadUrl, PDF, status().isOk());
    }

    @Test
    void downloadTokenCannotBeUsedToUpload() throws Exception {
        long id = publishedMaterial("2022 年初赛真题", "PAPER", "PUBLIC");
        String downloadUrl = downloadUrl(id, studentToken).get("url").asText();

        // 用途进了签名，所以下载令牌拿到上传接口上必须被拒
        String asUpload = downloadUrl.replace("/api/v1/materials/download", "/api/v1/admin/materials/upload");
        upload(asUpload, PDF, status().isForbidden());
    }

    @Test
    void publishRequiresCompleteSourceAndDraftStaysHidden() throws Exception {
        String objectKey = uploadedObjectKey("需要授权的真题.pdf");

        // LICENSED 少了 licenseRef，发布要被来源闸门拦住
        JsonNode rejected = perform(post("/api/v1/admin/materials"),
                materialBody("华杯赛真题", "PAPER", "LICENSED", objectKey, null, true),
                adminToken, status().isBadRequest());
        assertEquals("VALIDATION_ERROR", rejected.get("code").asText());

        // 同样的内容存草稿是允许的：先上传、后补授权是真实的工作顺序
        JsonNode draft = perform(post("/api/v1/admin/materials"),
                materialBody("华杯赛真题", "PAPER", "LICENSED", objectKey, null, false),
                adminToken, status().isOk());
        assertEquals("DRAFT", draft.get("status").asText());

        long id = draft.get("id").asLong();
        assertTrue(findInPublicList(id) == null, "草稿不该出现在公开列表里");
        perform(post("/api/v1/materials/{id}/download-url", id), "{}", studentToken, status().isNotFound());
    }

    @Test
    void publicListFiltersByTypeAndHidesObjectKey() throws Exception {
        long paper = publishedMaterial("2021 年真题", "PAPER", "OWNED");
        long handout = publishedMaterial("行程问题讲义", "HANDOUT", "OWNED");

        JsonNode papers = publicList("?type=PAPER");
        assertTrue(containsId(papers, paper));
        assertTrue(!containsId(papers, handout), "type 筛选不该混进讲义");

        JsonNode item = findInPublicList(paper);
        assertTrue(item.has("title") && item.has("originType"));
        assertTrue(!item.has("objectKey"), "公开列表不能暴露对象键，那等于把文件地址公开了");
    }

    @Test
    void downloadNeedsSignatureAndLogsAudit() throws Exception {
        long id = publishedMaterial("2020 年复赛真题", "PAPER", "PUBLIC");

        // 没有签名的裸地址不该给文件
        mockMvc.perform(get("/api/v1/materials/download")).andExpect(status().isBadRequest());

        JsonNode signed = downloadUrl(id, studentToken);
        assertTrue(signed.hasNonNull("expiresAt"));
        mockMvc.perform(get(signed.get("url").asText()))
                .andExpect(status().isOk());
        assertEquals(1, auditCount(id, "MATERIAL_DOWNLOAD"));

        // 匿名不能换签名地址，但拿到地址后可以直接下（浏览器跳转带不了 Authorization 头）
        perform(post("/api/v1/materials/{id}/download-url", id), "{}", null, status().isUnauthorized());
    }

    @Test
    void takedownInvalidatesAlreadyIssuedLinksImmediately() throws Exception {
        long id = publishedMaterial("要下架的真题", "PAPER", "PUBLIC");
        String url = downloadUrl(id, studentToken).get("url").asText();
        mockMvc.perform(get(url)).andExpect(status().isOk());

        perform(post("/api/v1/admin/materials/{id}/takedown", id), "{}", adminToken, status().isOk());

        // 规格允许旧链接最多 5 分钟失效，这里每次下载都回查状态，所以是立刻失效
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
        assertEquals("HIDDEN", statusOf(id));
        assertTrue(fileDeletedAt(id) != null, "下架要给文件打延迟清理的标记");
        assertEquals(1, auditCount(id, "MATERIAL_TAKEDOWN"));

        perform(post("/api/v1/admin/materials/{id}/takedown", id), "{}", studentToken, status().isForbidden());
    }

    // ---------- 请求构造 ----------

    private String ticketBody(String fileName, long sizeBytes, String sha256) {
        return """
                {"fileName":"%s","mimeType":"application/pdf","sizeBytes":%d,"sha256":"%s"}
                """.formatted(fileName, sizeBytes, sha256);
    }

    private JsonNode ticket(String fileName, byte[] bytes) throws Exception {
        return perform(post("/api/v1/admin/materials/upload-ticket"),
                ticketBody(fileName, bytes.length, sha256(bytes)), adminToken, status().isOk());
    }

    private void upload(String url, byte[] bytes, ResultMatcher expected) throws Exception {
        mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(bytes))
                .andExpect(expected);
    }

    private String uploadedObjectKey(String fileName) throws Exception {
        JsonNode ticket = ticket(fileName, PDF);
        upload(ticket.get("uploadUrl").asText(), PDF, status().isOk());
        return ticket.get("objectKey").asText();
    }

    private String materialBody(String title, String type, String originType, String objectKey,
                                String extra, boolean publish) {
        return """
                {"title":"%s","type":"%s","grade":"六年级","year":2023,"originType":"%s",
                 %s"objectKey":"%s","publish":%s}
                """.formatted(title, type, originType, extra == null ? "" : extra + ",", objectKey, publish);
    }

    private long publishedMaterial(String title, String type, String originType) throws Exception {
        String objectKey = uploadedObjectKey(title + ".pdf");
        String extra = switch (originType) {
            case "LICENSED" -> "\"licenseRef\":\"主办方书面授权 2026-001\"";
            case "PUBLIC" -> "\"sourceUrl\":\"https://example.org/paper.pdf\"";
            default -> null;
        };
        JsonNode created = perform(post("/api/v1/admin/materials"),
                materialBody(title, type, originType, objectKey, extra, true), adminToken, status().isOk());
        assertEquals("PUBLISHED", created.get("status").asText());
        return created.get("id").asLong();
    }

    private JsonNode downloadUrl(long id, String token) throws Exception {
        return perform(post("/api/v1/materials/{id}/download-url", id), "{}", token, status().isOk());
    }

    private JsonNode publicList(String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/materials" + query))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
    }

    private JsonNode findInPublicList(long id) throws Exception {
        for (JsonNode item : publicList("")) {
            if (item.get("id").asLong() == id) {
                return item;
            }
        }
        return null;
    }

    private static boolean containsId(JsonNode array, long id) {
        for (JsonNode item : array) {
            if (item.get("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }

    // ---------- 库内断言 ----------

    private String statusOf(long id) {
        return jdbc.queryForObject("SELECT status FROM material WHERE id = ?", String.class, id);
    }

    private Object fileDeletedAt(long id) {
        return jdbc.queryForObject("SELECT deleted_at FROM material_file WHERE material_id = ?", Object.class, id);
    }

    private int auditCount(long id, String action) {
        Integer rows = jdbc.queryForObject("""
                SELECT COUNT(1) FROM audit_log
                 WHERE action = ? AND target_type = 'MATERIAL' AND target_id = ?
                """, Integer.class, action, id);
        return rows == null ? 0 : rows;
    }

    // ---------- 通用 ----------

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private String registerToken(String nickname) throws Exception {
        String body = """
                {"nickname":"%s","email":"%s","password":"demo12345"}
                """.formatted(nickname, "material-" + UUID.randomUUID() + "@example.com");
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
