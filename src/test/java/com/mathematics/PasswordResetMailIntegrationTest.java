package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * R20 邮件通道端到端：验证码真的进了发给本人的那封邮件，拿邮件里的码能重置密码。
 * 发送是事务提交后异步进行的，所以断言都带等待。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:password-reset-mail;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "NON_KEYWORDS=USER,YEAR,VALUE;DB_CLOSE_DELAY=-1",
        "mathematics.notification.mail.enabled=true",
        "mathematics.notification.mail.from=no-reply@test.local",
        "mathematics.auth.log-reset-codes=false"})
@AutoConfigureMockMvc
class PasswordResetMailIntegrationTest {

    private static final Pattern SIX_DIGITS = Pattern.compile("(\\d{6})");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void codeArrivesByMailAndResetsThePassword() throws Exception {
        String email = "mail-" + UUID.randomUUID() + "@example.com";
        post("/api/v1/auth/register", """
                {"nickname":"收信人","email":"%s","password":"demo12345"}""".formatted(email), 200);

        post("/api/v1/auth/password/reset-code", "{\"account\":\"" + email + "\"}", 200);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000)).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertEquals(email, message.getTo()[0]);
        assertEquals("no-reply@test.local", message.getFrom());
        Matcher code = SIX_DIGITS.matcher(message.getText());
        assertTrue(code.find(), "邮件正文里要有 6 位验证码");

        post("/api/v1/auth/password/reset", """
                {"account":"%s","code":"%s","newPassword":"brandnew9876"}""".formatted(email, code.group(1)), 200);
        post("/api/v1/auth/login", """
                {"account":"%s","password":"brandnew9876"}""".formatted(email), 200);
    }

    @Test
    void unknownAccountSendsNothing() throws Exception {
        post("/api/v1/auth/password/reset-code", "{\"account\":\"ghost-" + UUID.randomUUID() + "@example.com\"}", 200);
        verify(mailSender, after(500).never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    private void post(String url, String body, int expectedStatus) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }
}
