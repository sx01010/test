package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 题目配图：只有管理员能传，类型看魔数，SVG 拒绝一切可执行内容，读取匿名可用且带安全响应头。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:problem-asset;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "NON_KEYWORDS=USER,YEAR,VALUE;DB_CLOSE_DELAY=-1",
        "mathematics.material.storage-dir=./target/test-assets"})
@AutoConfigureMockMvc
class ProblemAssetIntegrationTest {

    /** 8 字节 PNG 签名加一点内容就够魔数判断用了，服务端不解码图片。 */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    private static final String CLEAN_SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><use href="#a"/><circle id="a" r="4"/></svg>
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void accounts() throws Exception {
        adminToken = register("配图管理员");
        jdbc.update("UPDATE `user` SET role = 'ADMIN' WHERE id = ?", userIdOf(adminToken));
        studentToken = register("学生");
    }

    @Test
    void adminUploadsPngAndAnyoneCanReadItWithSafeHeaders() throws Exception {
        JsonNode uploaded = upload(PNG, adminToken, status().isOk());
        String key = uploaded.get("key").asText();
        assertEquals("![](asset:" + key + ")", uploaded.get("markdown").asText());

        MvcResult served = mockMvc.perform(get("/api/v1/assets/{key}", key))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andReturn();
        assertArrayEquals(PNG, served.getResponse().getContentAsByteArray());
    }

    @Test
    void sameBytesReuseTheSameKey() throws Exception {
        byte[] svg = CLEAN_SVG.replace("r=\"4\"", "r=\"" + UUID.randomUUID().hashCode() + "\"")
                .getBytes(StandardCharsets.UTF_8);
        String first = upload(svg, adminToken, status().isOk()).get("key").asText();
        String second = upload(svg, adminToken, status().isOk()).get("key").asText();
        assertEquals(first, second);
    }

    @Test
    void onlyAdminsCanUpload() throws Exception {
        upload(PNG, studentToken, status().isForbidden());
        upload(PNG, null, status().isUnauthorized());
    }

    @Test
    void contentTypeIsDecidedByMagicBytesNotTheHeader() throws Exception {
        upload("<html><body>hi</body></html>".getBytes(StandardCharsets.UTF_8), adminToken, status().isBadRequest());
        upload("%PDF-1.7".getBytes(StandardCharsets.UTF_8), adminToken, status().isBadRequest());
    }

    @Test
    void svgWithExecutableOrExternalContentIsRejected() throws Exception {
        String[] hostile = {
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><circle r=\"3\" onload=\"alert(1)\"/></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><a href=\"javascript:alert(1)\"><text>x</text></a></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><image href=\"https://evil.example/x.png\"/></svg>",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><foreignObject><div>x</div></foreignObject></svg>",
                "<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY x \"y\">]><svg xmlns=\"http://www.w3.org/2000/svg\"/>"
        };
        for (String svg : hostile) {
            upload(svg.getBytes(StandardCharsets.UTF_8), adminToken, status().isBadRequest());
        }
    }

    @Test
    void unknownOrMalformedKeysAre404() throws Exception {
        mockMvc.perform(get("/api/v1/assets/{key}", "a".repeat(32))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/assets/{key}", "..%2F..%2Fetc%2Fpasswd")).andExpect(status().isNotFound());
    }

    @Test
    void oversizeImageIsRejected() throws Exception {
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        upload(big, adminToken, status().isBadRequest());
    }

    private JsonNode upload(byte[] bytes, String token, ResultMatcher expected) throws Exception {
        var request = post("/api/v1/admin/assets").contentType(MediaType.APPLICATION_OCTET_STREAM).content(bytes);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        String payload = mockMvc.perform(request).andExpect(expected).andReturn().getResponse().getContentAsString();
        JsonNode node = payload.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(payload);
        assertTrue(node.isObject());
        return node;
    }

    private String register(String nickname) throws Exception {
        String body = """
                {"nickname":"%s","email":"%s","password":"demo12345"}
                """.formatted(nickname, "asset-" + UUID.randomUUID() + "@example.com");
        String payload = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(payload).get("accessToken").asText();
    }

    private long userIdOf(String token) throws Exception {
        String payload = mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(payload).get("id").asLong();
    }
}
