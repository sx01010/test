package com.mathematics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 切面层：{@code @RateLimit} 与 {@code @RequireLogin} / {@code @RequireAdmin}。
 *
 * <p>每个用例用不同的 remoteAddr，内存计数器在同一个上下文里共享，不隔开就会互相吃额度。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rate-limit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "NON_KEYWORDS=USER,YEAR,VALUE;DB_CLOSE_DELAY=-1",
        "mathematics.rate-limit.enabled=true"})
@AutoConfigureMockMvc
@DirtiesContext
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resetCodeIsCappedPerIpAndAnswers429WithRetryAfter() throws Exception {
        String ip = "10.0.0.1";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(resetCode(ip, "someone-" + i + "@example.com")).andExpect(status().isOk());
        }
        mockMvc.perform(resetCode(ip, "someone-else@example.com"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));

        mockMvc.perform(resetCode("10.0.0.2", "someone-else@example.com")).andExpect(status().isOk());
    }

    @Test
    void forgedForwardedForDoesNotBuyFreshQuota() throws Exception {
        String ip = "10.0.1.1";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(resetCode(ip, "x" + i + "@example.com").header("X-Forwarded-For", "1.2.3." + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(resetCode(ip, "y@example.com").header("X-Forwarded-For", "9.9.9.9"))
                .andExpect(status().isTooManyRequests());
    }

    /** 同一个账号换着 IP 猜也会被按账号的规则挡住，429 和账号存不存在无关。 */
    @Test
    void loginIsAlsoCappedPerAccountAcrossIps() throws Exception {
        String account = "nobody-" + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(login("10.0.2." + i, account)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login("10.0.2.99", account.toUpperCase())).andExpect(status().isTooManyRequests());
        mockMvc.perform(login("10.0.2.99", "other-" + account)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginRequiredEndpointsRejectAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/me/progress")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/problems")).andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder resetCode(String ip, String account) {
        return post("/api/v1/auth/password/reset-code").with(remote(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + account + "\"}");
    }

    private static MockHttpServletRequestBuilder login(String ip, String account) {
        return post("/api/v1/auth/login").with(remote(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + account + "\",\"password\":\"wrong-password\"}");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor remote(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
