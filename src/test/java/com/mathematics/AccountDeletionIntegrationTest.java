package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:account-deletion;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "NON_KEYWORDS=USER,YEAR,VALUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AccountDeletionIntegrationTest {

    private static final String PASSWORD = "demo12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private String email;
    private long userId;
    private String accessToken;
    private String refreshToken;

    @BeforeEach
    void register() throws Exception {
        email = "bye-" + UUID.randomUUID() + "@example.com";
        JsonNode issued = register(email);
        userId = issued.get("userId").asLong();
        accessToken = issued.get("accessToken").asText();
        refreshToken = issued.get("refreshToken").asText();
    }

    @Test
    void deletionAnonymizesAndKillsEverySession() throws Exception {
        mockMvc.perform(deleteMe(accessToken, PASSWORD)).andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT nickname, email, phone, status, deleted_at FROM `user` WHERE id = ?", userId);
        assertEquals("已注销用户", row.get("nickname"));
        assertNull(row.get("email"));
        assertNull(row.get("phone"));
        assertEquals("DELETED", row.get("status"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'ACCOUNT_DELETE' AND target_id = ?",
                Integer.class, userId));
    }

    @Test
    void sameEmailCanRegisterAgainAfterDeletion() throws Exception {
        mockMvc.perform(deleteMe(accessToken, PASSWORD)).andExpect(status().isNoContent());
        JsonNode again = register(email);
        assertEquals(false, again.get("userId").asLong() == userId);
    }

    @Test
    void wrongPasswordKeepsTheAccount() throws Exception {
        mockMvc.perform(deleteMe(accessToken, "not-my-password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminCannotSelfDelete() throws Exception {
        jdbc.update("UPDATE `user` SET role = 'ADMIN' WHERE id = ?", userId);
        mockMvc.perform(deleteMe(accessToken, PASSWORD)).andExpect(status().isForbidden());
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM `user` WHERE id = ?", String.class, userId));
    }

    @Test
    void anonymousCannotDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void privacyPolicyIsPublic() throws Exception {
        mockMvc.perform(get("/privacy.html")).andExpect(status().isOk());
    }

    private JsonNode register(String account) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"要走的人\",\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(account, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder deleteMe(
            String token, String password) {
        return delete("/api/v1/users/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}");
    }
}
